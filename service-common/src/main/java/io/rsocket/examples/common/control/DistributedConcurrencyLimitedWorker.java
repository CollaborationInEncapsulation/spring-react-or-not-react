package io.rsocket.examples.common.control;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class DistributedConcurrencyLimitedWorker implements Worker {

	final Bucket rateLimiter;
	final ScheduledExecutorService scheduledExecutorService;

	public Mono<Void> execute(Supplier<Mono<Void>> task) {
		return task.get()
		           .delaySubscription(Mono.fromCompletionStage(rateLimiter.asAsyncScheduler().tryConsume(1, Duration.ofSeconds(15), scheduledExecutorService)));
	}
}
