package ru.spring.demo.reactive.bigbro.services;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import ru.spring.demo.reactive.starter.speed.AdjustmentProperties;
import ru.spring.demo.reactive.starter.speed.model.DecodedLetter;
import ru.spring.demo.reactive.starter.speed.model.Letter;

import org.springframework.stereotype.Service;

/**
 * @author Evgeny Borisov
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LetterDecoder {

	final AdjustmentProperties adjustmentProperties;

	@SneakyThrows
	public Mono<DecodedLetter> decode(Letter letter) {

		AdjustmentProperties.ProcessingProperties processing =
				adjustmentProperties.getProcessing();
		int constantTime = processing
				.getTime();
		int randomDelay = processing.getRandomDelay() == 0 ? 0 :
				ThreadLocalRandom.current()
		                                   .nextInt(processing.getRandomDelay());

		return Mono.delay(Duration.ofMillis(constantTime + randomDelay))
		           .map(__ -> DecodedLetter.builder()
	                                   .author(letter.secretMethodForDecodeSignature())
	                                   .location(letter.getLocation())
	                                   .content(letter.getContent())
	                                   .build());
	}
}
