package io.rsocket.examples.common.control;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

import com.netflix.concurrency.limits.limit.VegasLimit;
import io.rsocket.lease.Lease;
import io.rsocket.lease.LeaseStats;
import reactor.core.publisher.SignalType;
import reactor.util.annotation.Nullable;

public class VegaLimitLeaseStats implements LeaseStats {

	final UUID                 uuid;
	final VegaLimitLeaseSender parent;
	final VegasLimit           limitAlgorithm;

	final ConcurrentMap<Integer, Integer> inFlightMap = new ConcurrentHashMap<>();
	final ConcurrentMap<Integer, Long> timeMap = new ConcurrentHashMap<>();

	final LongSupplier clock = System::nanoTime;

	volatile Lease currentLease;

	public VegaLimitLeaseStats(UUID uuid, VegaLimitLeaseSender parent, VegasLimit limit) {
		this.uuid = uuid;
		this.parent = parent;
		this.limitAlgorithm = limit;
	}

	@Override
	public void onEvent(EventType eventType, int streamId, @Nullable SignalType releaseSignal) {
		if (eventType == EventType.RELEASE) {
			VegaLimitLeaseSender.IN_FLIGHT.decrementAndGet(parent);
			Long startTime = timeMap.get(streamId);
			Integer currentInflight = inFlightMap.get(streamId);

			limitAlgorithm.onSample(startTime, clock.getAsLong() - startTime, currentInflight, !(releaseSignal == SignalType.ON_COMPLETE));
			parent.release();
		}
		if (eventType == EventType.ACCEPT) {
			long startTime = clock.getAsLong();
			int currentInFlight = VegaLimitLeaseSender.IN_FLIGHT.incrementAndGet(parent);

			inFlightMap.put(streamId, currentInFlight);
			timeMap.put(streamId, startTime);
		}
	}

	@Override
	public void onClose() {
		parent.remove(this);
	}

	public void onNewLease(Lease lease) {
		currentLease = lease;
	}
}
