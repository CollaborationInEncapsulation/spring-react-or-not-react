package io.rsocket.examples.common.control;

import java.util.function.Supplier;

import reactor.core.publisher.Mono;

public interface Worker {

	default Mono<Void> execute(Supplier<Mono<Void>> task) {
		return task.get();
	}
}
