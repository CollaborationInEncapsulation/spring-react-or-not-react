package io.rsocket.examples.service.smith.decider;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.rsocket.examples.common.model.DecodedLetter;
import io.rsocket.examples.common.model.Notification;
import io.rsocket.examples.common.processing.Delayer;
import io.rsocket.examples.service.smith.notifier.Notifier;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GuardDecider {

	final Delayer  delayer;
	final Notifier notifier;
	final Counter  counter;

	public GuardDecider(
			Delayer delayer,
			Notifier notifier,
			MeterRegistry meterRegistry) {
		this.delayer = delayer;
		this.notifier = notifier;
		this.counter = meterRegistry.counter("letter.rps");
	}

	public Mono<Void> decide(DecodedLetter notification) {

		return Mono
			           .delay(delayer.nextDelay())
			           .flatMap(__ -> notifier
				           .sendNotification(getDecision(notification))
				           .doFinally(___ -> counter.increment())
			           );
	}

	private static Notification getDecision(DecodedLetter decodedLetter) {
		int decision = (int) ((Math.random() * (2)) + 1);
		if (decision == 1) {
			return Notification.builder()
			                   .author(decodedLetter.getAuthor())
			                   .action("Nothing")
			                   .build();
		}
		else {
			return Notification.builder()
			                   .author(decodedLetter.getAuthor())
			                   .action("Block")
			                   .build();
		}
	}
}
