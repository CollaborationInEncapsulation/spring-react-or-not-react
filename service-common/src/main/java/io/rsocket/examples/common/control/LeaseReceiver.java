package io.rsocket.examples.common.control;

import java.util.function.Consumer;

import io.rsocket.Payload;
import io.rsocket.lease.Lease;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LeaseReceiver extends Consumer<Flux<Lease>>, Disposable {
	/**
	 * Subscribes to the given source if lease has been acquired successfully.
	 *
	 * If there are no available leases this method allows to listen to new incoming
	 * leases and delay some action (e.g . retry) until new valid lease has come in.
	 */
	<T> Mono<T> acquireLease(Mono<T> source);

	/**
	 * Subscribes to the given source if lease has been acquired successfully.
	 *
	 * If there are no available leases this method allows to listen to new incoming
	 * leases and delay some action (e.g . retry) until new valid lease has come in.
	 */
	Flux<Payload> acquireLease(Flux<Payload> source);
}
