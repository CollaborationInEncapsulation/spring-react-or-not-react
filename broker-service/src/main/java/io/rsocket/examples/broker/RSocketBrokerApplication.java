package io.rsocket.examples.broker;

import java.util.UUID;

import com.netflix.concurrency.limits.limit.VegasLimit;
import io.rsocket.examples.common.control.DefaultLeaseReceiver;
import io.rsocket.examples.common.control.LeaseReceiver;
import io.rsocket.examples.common.control.LeaseWaitingRSocket;
import io.rsocket.examples.common.control.VegaLimitLeaseSender;
import io.rsocket.examples.common.control.VegaLimitLeaseStats;
import io.rsocket.lease.Leases;
import io.rsocket.plugins.RSocketInterceptor;
import reactor.core.scheduler.Schedulers;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.rsocket.server.RSocketServerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
public class RSocketBrokerApplication {

	public static void main(String[] args) {
		SpringApplication.run(RSocketBrokerApplication.class, args);
	}

	@Configuration
	public static class BrokerLeasingConfiguration {

		static final ThreadLocal<LeaseReceiver> LEASE_RECEIVER = new ThreadLocal<>();

		@Bean
		public RSocketServerCustomizer rSocketBrokerServerCustomizer() {
			return rSocketServer ->
				rSocketServer
					.interceptors(ir -> ir.forRequester((RSocketInterceptor) r -> new LeaseWaitingRSocket(r, LEASE_RECEIVER.get())))
					.lease(() -> {
						UUID uuid = UUID.randomUUID();
						DefaultLeaseReceiver leaseReceiver = new DefaultLeaseReceiver(uuid);
						VegaLimitLeaseSender leaseSender = new VegaLimitLeaseSender(1000,
								// default vegaLimit concurrency
								1000,
								Schedulers.newSingle("lease-sender")
								          .createWorker());

						LEASE_RECEIVER.set(leaseReceiver);
						return Leases.create()
						             .sender(leaseSender)
						             .receiver(leaseReceiver)
						             .stats(new VegaLimitLeaseStats(uuid, leaseSender, VegasLimit.newDefault()));
					});
		}
 	}
}