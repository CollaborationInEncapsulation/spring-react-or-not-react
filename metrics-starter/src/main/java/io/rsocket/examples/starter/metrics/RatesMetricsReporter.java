package io.rsocket.examples.starter.metrics;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collector;
import java.util.stream.Stream;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import io.rsocket.metadata.WellKnownMimeType;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.pool.InstrumentedPool;
import reactor.util.retry.Retry;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.MediaType;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

import static java.util.Map.entry;
import static java.util.stream.Collectors.toMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class RatesMetricsReporter extends BaseSubscriber<Void> implements
                                                               InitializingBean,
                                                               DisposableBean {

	private static final Collector<Entry<String, Object>, ?, Map<String, Object>>
			ENTRY_MAP_COLLECTOR = toMap(Entry::getKey, Entry::getValue, (o, o2) -> {
		if (o instanceof List && o2 instanceof List) {
			((List) o).addAll((Collection) o2);
		}
		return o;
	});

	final MetricRegistry           metricRegistry;
	final InstrumentedPool<?>      pool;
	final RSocketRequester.Builder rSocketRequesterBuilder;
	final AtomicBoolean            status;
	final String                   serviceName;
	final int                      order;


	@Override
	public void afterPropertiesSet() {
		rSocketRequesterBuilder
			.rsocketConnector(connector -> connector.reconnect(
				Retry.backoff(Long.MAX_VALUE, Duration.ofMillis(100))
				     .maxBackoff(Duration.ofSeconds(5))
			))
			.metadataMimeType(MimeType.valueOf(WellKnownMimeType.MESSAGE_RSOCKET_COMPOSITE_METADATA.getString()))
			.dataMimeType(MediaType.APPLICATION_JSON)
			.setupData(serviceName)
			.connectTcp("localhost", 9090)
            .flatMap(rSocketRequester ->
            	Flux.interval(Duration.ofMillis(100))
	                .onBackpressureDrop()
	                .concatMap(__ -> rSocketRequester.route("report")
	                                                 .data(extractRatesFromMeters())
	                                                 .send(),   1)
		            .then()
            )
			.retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofMillis(100))
			                .maxBackoff(Duration.ofSeconds(10))
			)
			.subscribe(this);
	}

	@Override
	public void destroy() {
		dispose();
	}

	private Map<String, Object> extractRatesFromMeters() {
		Map<String, Object> rates = extractMeterRates(metricRegistry.getMeters()
		                                                            .entrySet()
		                                                            .stream());

 		rates.putAll(extractPoolStats("common", pool));
		rates.put("component", serviceName);
		rates.put("order", order);
		rates.put("status", status.get() && !pool.isDisposed() ? "UP" :
				pool.isDisposed() ? "DOWN" : "FAIL");
		return rates;
	}

	private Map<String, Object> extractMeterRates(Stream<Entry<String, Meter>> stream) {
		return stream.map(metric -> entry(metric.getKey(),
				(Object) metric.getValue()
				               .getOneMinuteRate()))
		             .collect(ENTRY_MAP_COLLECTOR);
	}

	private Map<String, Object> extractPoolStats(String name,
			InstrumentedPool<?> pool) {
		InstrumentedPool.PoolMetrics metrics = pool.metrics();
		int threadInWork = metrics.acquiredSize();
		int maximumPoolSize = metrics.getMaxAllocatedSize();
		int queueSize = metrics.getMaxPendingAcquireSize();
		int remainingCapacity = queueSize - metrics.pendingAcquireSize();

		return new ImmutableMap.Builder<String, Object>().put("buffers",
				Arrays.asList(new BufferStats().setName(name)
				                               .setRemaining(remainingCapacity)
				                               .setMaxSize(queueSize)
				                               .setActiveWorker(threadInWork)
				                               .setWorkersCount(maximumPoolSize)))
		                                                 .put("buffer.size",
				                                                 remainingCapacity)
		                                                 .put("buffer.capacity",
				                                                 queueSize)
		                                                 .build();
	}

	@Data
	@Accessors(chain = true)
	public static class BufferStats {

		String name;
		int    remaining;
		int    maxSize;
		int    activeWorker;
		int    workersCount;
	}
}
