package io.rsocket.examples.service.bigbro.services;

import io.rsocket.examples.common.model.DecodedLetter;
import io.rsocket.examples.common.model.Letter;
import io.rsocket.examples.common.processing.Delayer;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import org.springframework.stereotype.Service;

/**
 * @author Evgeny Borisov
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LetterDecoder {

	final Delayer delayer;

	@SneakyThrows
	public Mono<DecodedLetter> decode(Letter letter) {

		return Mono.delay(delayer.nextDelay())
		           .map(__ -> DecodedLetter.builder()
	                                   .author(letter.secretMethodForDecodeSignature())
	                                   .location(letter.getLocation())
	                                   .content(letter.getContent())
	                                   .build());
	}
}
