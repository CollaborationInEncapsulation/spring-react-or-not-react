package io.rsocket.examples.common.control;

import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.util.RSocketProxy;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class LeaseWaitingRSocket extends RSocketProxy {

	final LeaseReceiver leaseReceiver;

	public LeaseWaitingRSocket(RSocket source, LeaseReceiver leaseReceiver) {
		super(source);
		this.leaseReceiver = leaseReceiver;
	}

	@Override
	public Mono<Void> fireAndForget(Payload payload) {
		return leaseReceiver.acquireLease(super.fireAndForget(payload));
	}

	@Override
	public Mono<Payload> requestResponse(Payload payload) {
		return leaseReceiver.acquireLease(super.requestResponse(payload));
	}

	@Override
	public Flux<Payload> requestStream(Payload payload) {
		return leaseReceiver.acquireLease(super.requestStream(payload));
	}

	@Override
	public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
		return leaseReceiver.acquireLease(super.requestChannel(payloads));
	}
}