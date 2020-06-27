package ru.spring.demo.reactive.smith.decider;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import ru.spring.demo.reactive.smith.notifier.Notifier;
import ru.spring.demo.reactive.starter.speed.AdjustmentProperties;
import ru.spring.demo.reactive.starter.speed.model.DecodedLetter;
import ru.spring.demo.reactive.starter.speed.model.Notification;

import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GuardDecider {

	final AdjustmentProperties adjustmentProperties;
	final Notifier             notifier;
	final Counter              counter;

	public GuardDecider(
			AdjustmentProperties adjustmentProperties,
			Notifier notifier,
			MeterRegistry meterRegistry) {
		this.adjustmentProperties = adjustmentProperties;
		this.notifier = notifier;
		this.counter = meterRegistry.counter("letter.rps");
	}

	public Mono<Void> decide(DecodedLetter notification) {
		AdjustmentProperties.ProcessingProperties processing =
				adjustmentProperties.getProcessing();
		int constantTime = processing
				.getTime();
		int randomDelay = processing.getRandomDelay() == 0 ? 0 :
				ThreadLocalRandom.current()
				                 .nextInt(processing.getRandomDelay());
		return Mono
			           .delay(Duration.ofMillis(constantTime + randomDelay))
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
