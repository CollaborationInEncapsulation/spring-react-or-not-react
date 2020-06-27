package ru.spring.demo.reactive.starter.speed.support;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import com.netflix.concurrency.limits.Limiter;
import io.github.bucket4j.Bucket;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import lombok.RequiredArgsConstructor;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;
import reactor.core.scheduler.Schedulers;

@RequiredArgsConstructor
public class DistributedConcurrencyLimitedWorker implements Worker {

	final Bucket rateLimiter;
	final ScheduledExecutorService scheduledExecutorService;

	public Mono<Void> execute(Supplier<Mono<Void>> task) {
		return task.get()
		           .delaySubscription(Mono.fromCompletionStage(rateLimiter.asAsyncScheduler().tryConsume(1, Duration.ofSeconds(15), scheduledExecutorService)));
	}
}
