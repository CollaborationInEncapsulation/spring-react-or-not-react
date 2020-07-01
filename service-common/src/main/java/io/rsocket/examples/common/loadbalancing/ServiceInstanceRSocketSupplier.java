package io.rsocket.examples.common.loadbalancing;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

import io.rsocket.RSocket;
import io.rsocket.client.LoadBalancedRSocketMono;
import io.rsocket.client.filter.RSocketSupplier;
import io.rsocket.core.RSocketConnector;
import io.rsocket.examples.common.control.DefaultLeaseReceiver;
import io.rsocket.examples.common.control.LeaseReceiver;
import io.rsocket.examples.common.control.LeaseWaitingRSocket;
import io.rsocket.lease.Leases;
import io.rsocket.plugins.RSocketInterceptor;
import io.rsocket.transport.netty.client.WebsocketClientTransport;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;
import reactor.util.retry.Retry;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.util.MimeType;

public class ServiceInstanceRSocketSupplier extends RSocketSupplier {

	static final ThreadLocal<LeaseReceiver> LEASE_RECEIVER = new ThreadLocal<>();

	final ServiceInstance serviceInstance;

	public ServiceInstanceRSocketSupplier(
			MimeType dataMimeType,
			MimeType metadataMimeType,
			ServiceInstance serviceInstance) {

		super(() -> {
			Mono<RSocket> reconnectable = RSocketConnector
					.create()
					.interceptors(ir -> ir.forRequester((RSocketInterceptor) r -> new LeaseWaitingRSocket(r, LEASE_RECEIVER.get())))
					.lease(() -> {
						UUID uuid = UUID.randomUUID();
						DefaultLeaseReceiver leaseReceiver = new DefaultLeaseReceiver(uuid);

						LEASE_RECEIVER.set(leaseReceiver);
						return Leases.create()
						             .receiver(leaseReceiver);
					})
					.dataMimeType(dataMimeType.toString())
					.metadataMimeType(metadataMimeType.toString())
					.reconnect(Retry.backoff(Long.MAX_VALUE, Duration.ofMillis(100))
					                .maxBackoff(Duration.ofSeconds(5)))
					.connect(WebsocketClientTransport.create(serviceInstance.getUri()
					                                                        .resolve("/rsocket")));
			reconnectable.block();

			return reconnectable;
		});
		this.serviceInstance = serviceInstance;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		ServiceInstanceRSocketSupplier supplier = (ServiceInstanceRSocketSupplier) o;

		return serviceInstance.equals(supplier.serviceInstance);
	}

	@Override
	public int hashCode() {
		return serviceInstance.hashCode();
	}
}
