package io.rsocket.examples.common.processing;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public class Delayer {

	final long baseDelayMillis;
	final long randomDelayMillis;

	public Delayer(long baseDelayMillis, long randomDelayMillis) {
		this.baseDelayMillis = baseDelayMillis;
		this.randomDelayMillis = randomDelayMillis;
	}

	public Duration nextDelay() {
		long nextDelayMillis = baseDelayMillis;

		if (randomDelayMillis > 0) {
			nextDelayMillis += ThreadLocalRandom.current().nextLong(randomDelayMillis);
		}

		return Duration.ofMillis(nextDelayMillis);
	}
}
