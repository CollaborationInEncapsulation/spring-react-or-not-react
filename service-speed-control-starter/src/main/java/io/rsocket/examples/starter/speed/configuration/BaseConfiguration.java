package io.rsocket.examples.starter.speed.configuration;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.netflix.concurrency.limits.limit.VegasLimit;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.grid.GridBucketState;
import io.github.bucket4j.grid.RecoveryStrategy;
import io.github.bucket4j.grid.hazelcast.Hazelcast;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.rsocket.examples.common.control.DistributedConcurrencyLimitedWorker;
import io.rsocket.examples.common.control.ErrorStrategy;
import io.rsocket.examples.common.control.LeaseReceiver;
import io.rsocket.examples.common.control.OverflowStrategy;
import io.rsocket.examples.common.control.RateLimitedWorker;
import io.rsocket.examples.common.control.SimpleWorker;
import io.rsocket.examples.common.control.VegaLimitLeaseSender;
import io.rsocket.examples.common.control.VegaLimitLeaseStats;
import io.rsocket.examples.common.control.WaitFreeInstrumentedPool;
import io.rsocket.examples.common.control.Worker;
import io.rsocket.examples.common.loadbalancing.ServiceInstanceRSocketSupplier;
import io.rsocket.examples.common.processing.Delayer;
import io.rsocket.examples.starter.speed.AdjustmentProperties;
import io.rsocket.client.LoadBalancedRSocketMono;
import io.rsocket.client.filter.RSocketSupplier;
import io.rsocket.lease.Leases;
import io.rsocket.metadata.WellKnownMimeType;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.pool.InstrumentedPool;
import reactor.pool.PoolBuilder;
import reactor.util.retry.Retry;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.hazelcast.HazelcastInstanceFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.rsocket.server.RSocketServerCustomizer;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import static io.rsocket.examples.starter.speed.configuration.RSocketRequesterSetupUtility.getDataMimeType;

/**
 * @author Evgeny Borisov
 * @author Kirill Tolkachev
 * @author Oleh Dokuka
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AdjustmentProperties.class)
public class BaseConfiguration {

	static final ThreadLocal<LeaseReceiver> LEASE_RECEIVER = new ThreadLocal<>();

	@Bean
	public Mono<RSocketRequester> rSocketRequester(
			RSocketStrategies rSocketStrategies,
			AdjustmentProperties adjustmentProperties,
			ReactiveDiscoveryClient discoveryClient) {
		MimeType dataMimeType = getDataMimeType(rSocketStrategies);
		MimeType metadataMimeType =
				MimeTypeUtils.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_COMPOSITE_METADATA.getString());

		return Flux
			.interval(Duration.ZERO, Duration.ofMillis(1000))
		    .onBackpressureDrop()
		    .concatMap(__ ->
			    discoveryClient
				    .getInstances(adjustmentProperties.getReceiver().getServiceName())
					.map(si -> new ServiceInstanceRSocketSupplier(dataMimeType, metadataMimeType, si))
					.collectList()
				    .map(l -> (Collection<RSocketSupplier>)(Collection) l),
		        1
		    )
			.as(LoadBalancedRSocketMono::create)
			.filter(r -> !r.getClass().getSimpleName().contains("FailingRSocket"))
			.repeatWhenEmpty(f -> f.concatMap(i -> Mono.delay(Duration.ofSeconds(1))))
			.map(rsocket -> RSocketRequester.wrap(
					rsocket,
					dataMimeType,
					metadataMimeType,
					rSocketStrategies
			));
	}

	@Bean
	public RSocketServerCustomizer rSocketServerCustomizer(AdjustmentProperties properties, VegaLimitLeaseSender leaseSender) {
		return rSocketServer -> rSocketServer
				.lease(() ->
					Leases.create()
					      .sender(leaseSender)
					      .stats(new VegaLimitLeaseStats(
					      		UUID.randomUUID(),
					      		leaseSender,
						        VegasLimit.newBuilder()
						                  .initialLimit(1)
						                  .maxConcurrency(properties.getProcessing().getConcurrencyLevel())
						                  .build()
					      ))
				);
	}

	@Bean
	public VegaLimitLeaseSender vegaLeaseSender(AdjustmentProperties properties) {
		AdjustmentProperties.ProcessingProperties processingProperties = properties.getProcessing();
		return new VegaLimitLeaseSender(
				processingProperties.getConcurrencyLevel(),
				processingProperties.getTime(),
				Schedulers.newSingle("lease-sender").createWorker()
		);
	}

	@Bean
	@ConditionalOnProperty(value = "letter.sender.rate-limit.distributed", havingValue = "true")
	public HazelcastInstance hazelcastInstance() {
		Config config = new Config();

		return new HazelcastInstanceFactory(config).getHazelcastInstance();
	}

	@Bean
	public Supplier<Worker> rateLimitedWorker(AdjustmentProperties adjustmentProperties, Optional<HazelcastInstance> hazelcastInstanceOptional) {
		AdjustmentProperties.RateLimitProperties rateLimitConfigurations = adjustmentProperties.getSender().getRateLimit();
		if (rateLimitConfigurations.isEnabled()) {

			if (rateLimitConfigurations.isDistributed()) {
				HazelcastInstance instance = hazelcastInstanceOptional.get();
				IMap<String, GridBucketState> buckets = instance.getMap("buckets");
				Bucket bucket = Bucket4j.extension(Hazelcast.class)
				                        .builder()
				                        .addLimit(Bandwidth.simple(rateLimitConfigurations.getLimit(), rateLimitConfigurations.getPeriod()))
				                        .build(buckets, "bigbro", RecoveryStrategy.RECONSTRUCT);

				ScheduledExecutorService scheduledExecutorService =
						Executors.newScheduledThreadPool(rateLimitConfigurations.getLimit());
				return () -> new DistributedConcurrencyLimitedWorker(bucket, scheduledExecutorService);
			}
			else {
				RateLimiterConfig rateLimiterConfig = RateLimiterConfig
						.custom()
						.limitForPeriod(rateLimitConfigurations.getLimit())
						.limitRefreshPeriod(rateLimitConfigurations.getPeriod())
						.build();
				RateLimiter rateLimiter = RateLimiter.of("workerLimiter", rateLimiterConfig);

				return () -> new RateLimitedWorker(RateLimiterOperator.of(rateLimiter));
			}
		}

		return SimpleWorker::new;
	}

	@Bean
	public InstrumentedPool<Worker> letterProcessorExecutor(AdjustmentProperties adjustmentProperties, Supplier<Worker> workerSupplier) {
		AdjustmentProperties.ProcessingProperties processingProperties =
				adjustmentProperties.getProcessing();
		InstrumentedPool<Worker> pool =
			PoolBuilder
				.from(Mono.fromSupplier(workerSupplier))
				.maxPendingAcquire(processingProperties.getQueueSize())
				.sizeBetween(processingProperties.getConcurrencyLevel(), processingProperties.getConcurrencyLevel())
				.evictionPredicate((wc, metadata) -> false)
				.lifo();

		if (processingProperties.getOverflowStrategy() == OverflowStrategy.BLOCK) {
			return pool;
		}
		return new WaitFreeInstrumentedPool<>(pool, processingProperties.getOverflowStrategy());
	}

	@Bean
	public Function<Mono<Void>, Mono<Void>> responseHandlerFunction(
		    AdjustmentProperties properties,
			@Qualifier("letter.status") AtomicBoolean status,
			MeterRegistry meterRegistry) {

		Counter speedCounter = meterRegistry.counter("letter.rps");
		Counter retriesCounter = meterRegistry.counter("letter.fps");
		Counter dropsCounter = meterRegistry.counter("letter.dps");
		ErrorStrategy errorStrategy = properties.getSender().getErrorStrategy();

		return mono -> mono
			.retryWhen(
					Retry.backoff(10, Duration.ofMillis(100))
					     .maxBackoff(Duration.ofSeconds(5))
					     .filter(t -> errorStrategy == ErrorStrategy.RETRY)
					     .doBeforeRetry(rs -> {
					     	retriesCounter.increment();
					     	log.info("Failed. Retrying. {}", rs);
					     })
			)
			.doOnSubscribe(__ -> speedCounter.increment())
			.doOnSuccess(__ -> log.info("Letter processed and sent"))
			.onErrorResume(e -> {
				switch (errorStrategy) {
					case DROP:
					case RETRY:
						dropsCounter.increment();
						return Mono.empty();
				}

				status.set(false);
				log.error("Letter processed but was not sent, terminate app ", e);
				return Mono.error(e);
			});
	}

	@Bean
	public Delayer delayer(AdjustmentProperties adjustmentProperties) {
		return new Delayer(
			adjustmentProperties.getProcessing().getTime(),
			adjustmentProperties.getProcessing().getRandomDelay()
		);
	}
}
