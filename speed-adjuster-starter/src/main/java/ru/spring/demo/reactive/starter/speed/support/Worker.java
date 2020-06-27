package ru.spring.demo.reactive.starter.speed.support;

import java.util.function.Supplier;

import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

public interface Worker {

	default Mono<Void> execute(Supplier<Mono<Void>> task) {
		return task.get();
	}
}
