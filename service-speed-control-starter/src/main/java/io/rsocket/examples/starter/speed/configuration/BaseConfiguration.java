package io.rsocket.examples.starter.speed.configuration;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
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
import io.rsocket.examples.common.control.OverflowStrategy;
import io.rsocket.examples.common.control.RateLimitedWorker;
import io.rsocket.examples.common.control.SimpleWorker;
import io.rsocket.examples.common.control.WaitFreeInstrumentedPool;
import io.rsocket.examples.common.control.Worker;
import io.rsocket.examples.common.processing.Delayer;
import io.rsocket.examples.starter.speed.AdjustmentProperties;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.pool.InstrumentedPool;
import reactor.pool.PoolBuilder;
import reactor.util.retry.Retry;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import static com.hazelcast.core.Hazelcast.newHazelcastInstance;

/**
 * @author Evgeny Borisov
 * @author Kirill Tolkachev
 * @author Oleh Dokuka
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AdjustmentProperties.class)
public class BaseConfiguration {

	@Bean
	public WebClient webClient(WebClient.Builder webClientBuilder, AdjustmentProperties adjustmentProperties) {
		return webClientBuilder.baseUrl(adjustmentProperties.getReceiver().getBaseUrl())
		                       .build();
	}

	@Bean
	@ConditionalOnProperty(value = "letter.sender.rate-limit.distributed", havingValue = "true")
	public HazelcastInstance hazelcastInstance() {
		Config config = new Config();

		return newHazelcastInstance(config);
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
				.buildPool();

		if (processingProperties.getOverflowStrategy() == OverflowStrategy.BLOCK) {
			return pool;
		}

		AdjustmentProperties.SenderProperties senderProperties =
				adjustmentProperties.getSender();

		return new WaitFreeInstrumentedPool<>(pool,
				processingProperties.getOverflowStrategy(),
				senderProperties.getErrorStrategy());
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
				     .filter(t -> (t.getMessage() != null && t.getMessage().contains("Connection refused")) || errorStrategy == ErrorStrategy.RETRY)
				     .doBeforeRetry(rs -> {
					     if (rs.failure().getMessage() != null && rs.failure().getMessage().contains("Connection refused")) {
						     return;
					     }
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
