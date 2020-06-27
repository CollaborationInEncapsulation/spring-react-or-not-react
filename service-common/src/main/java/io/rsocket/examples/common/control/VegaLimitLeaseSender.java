package io.rsocket.examples.common.control;

import java.util.Optional;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Function;

import io.rsocket.lease.Lease;
import io.rsocket.lease.LeaseStats;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxProcessor;
import reactor.core.publisher.UnicastProcessor;
import reactor.core.scheduler.Scheduler;
import reactor.util.concurrent.Queues;

public class VegaLimitLeaseSender implements Runnable, Function<Optional<LeaseStats>, Flux<Lease>> {

	static final Logger logger = LoggerFactory.getLogger(VegaLimitLeaseSender.class);

	final ConcurrentMap<VegaLimitLeaseStats, FluxProcessor<Lease, Lease>> info;
	final BlockingDeque<VegaLimitLeaseStats>                              leaseStatsQueue;
	final Scheduler.Worker                                                worker;
	final int                                                             capacity;
	final int                                                             ttl;

	volatile int activeConnections;
	static final AtomicIntegerFieldUpdater<VegaLimitLeaseSender> ACTIVE_CONNECTIONS =
			AtomicIntegerFieldUpdater.newUpdater(VegaLimitLeaseSender.class, "activeConnections");

	volatile int inFlight;
	static final AtomicIntegerFieldUpdater<VegaLimitLeaseSender> IN_FLIGHT =
			AtomicIntegerFieldUpdater.newUpdater(VegaLimitLeaseSender.class, "inFlight");



	public VegaLimitLeaseSender(int capacity, long ttl, Scheduler.Worker worker) {
		this.worker = worker;
		this.info = new ConcurrentHashMap<>();
		this.leaseStatsQueue = new LinkedBlockingDeque<>();

		if (ttl == 0) {
			this.ttl = Integer.MAX_VALUE;
			this.capacity = Integer.MAX_VALUE;
		}
		else {
			this.capacity = capacity;
			this.ttl = (int) ttl;
		}
	}

	@Override
	public Flux<Lease> apply(Optional<LeaseStats> stats) {
		if (stats.isEmpty()) {
			return Flux.error(new IllegalStateException("LeaseStats must be present"));
		}

		UnicastProcessor<Lease> leaseUnicastFluxProcessor =
				UnicastProcessor.create(Queues.<Lease>one().get());
		VegaLimitLeaseStats leaseStats = (VegaLimitLeaseStats) stats.get();

		logger.info("Received new leased Connection[" + leaseStats.uuid + "]");

		if (capacity == Integer.MAX_VALUE) {
			logger.info("To Connection[" + leaseStats.uuid + "]: Issued Unbounded Lease");
			leaseUnicastFluxProcessor.onNext(Lease.create(ttl, capacity));
		}
		else {
			info.put(leaseStats, leaseUnicastFluxProcessor);
			int currentActive = ACTIVE_CONNECTIONS.getAndIncrement(this);
			leaseStatsQueue.offer(leaseStats);

			if (currentActive == 0) {
				worker.schedule(this);
			}
		}

		return leaseUnicastFluxProcessor;
	}

	@Override
	public void run() {
		try {
			VegaLimitLeaseStats leaseStats = leaseStatsQueue.poll();

			if (leaseStats == null) {
				return;
			}

			FluxProcessor<Lease, Lease> leaseSender = info.get(leaseStats);

			if (leaseSender == null || leaseSender.isDisposed()) {
				logger.debug("Connection[" + leaseStats.uuid + "]: LeaseSender is Disposed");

				int currentActive = ACTIVE_CONNECTIONS.decrementAndGet(this);
				if (currentActive > 0) {
					worker.schedule(this);
				}
				return;
			}

			Lease nextLease = null;
			int limit = leaseStats.limitAlgorithm.getLimit();

			if (limit == 0) {
				throw new IllegalStateException("Limit is 0");
			}

			if (inFlight < capacity) {
				nextLease = Lease.create(ttl, limit);
			}

			if (nextLease == null) {
				leaseStatsQueue.addFirst(leaseStats);
				worker.schedule(this, 1, TimeUnit.MILLISECONDS);
				return;
			}

			logger.debug("To Connection[" + leaseStats.uuid + "]: Issued Lease: [" + nextLease + "]");

			leaseStats.onNewLease(nextLease);
			leaseSender.onNext(nextLease);

			leaseStatsQueue.offer(leaseStats);

			int activeConnections = info.size();
			int nextDelay = activeConnections == 0 ? ttl : (ttl / activeConnections);

			logger.debug("Next check after delay[" + nextDelay + "ms]");

			worker.schedule(this, nextDelay, TimeUnit.MILLISECONDS);
		}
		catch (Throwable e) {
			logger.error("LeaseSender failed to send lease", e);
		}
	}

	void release() {

	}

	void remove(VegaLimitLeaseStats stats) {
		info.remove(stats);
	}
}
