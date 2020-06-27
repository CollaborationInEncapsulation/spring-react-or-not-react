package ru.spring.demo.reactive.starter.speed.support;

import java.util.function.Supplier;

import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class RateLimitedWorker implements Worker {

	@SuppressWarnings("rawtypes")
	final RateLimiterOperator rateLimiter;

	@SuppressWarnings("unchecked")
	public Mono<Void> execute(Supplier<Mono<Void>> task) {
		return task.get()
		           .transform(rateLimiter);
	}
}
