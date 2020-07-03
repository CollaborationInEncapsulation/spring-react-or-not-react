package io.rsocket.examples.service.pechkin.producer;

import com.github.javafaker.RickAndMorty;
import io.micrometer.core.instrument.MeterRegistry;
import io.rsocket.examples.common.model.Letter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import org.springframework.stereotype.Service;

/**
 * @author Evgeny Borisov
 * @author Kirill Tolkachev
 * @author Oleh Dokuka
 */
@Slf4j
@Service
@Setter
public class LetterProducer {

	final RickAndMorty faker;

	public LetterProducer(RickAndMorty faker, MeterRegistry meterRegistry) {
		this.faker = faker;
	}

	@SneakyThrows
	public Letter getLetter() {
		return randomLetter();
	}

	public Flux<Letter> letterFlux() {
		return Flux.generate(synchronousSink -> synchronousSink.next(randomLetter()));
	}

	private Letter randomLetter() {
		String character = faker.character();
		return Letter.builder()
		             .content(faker.quote())
		             .location(faker.location())
		             .signature(character)
		             ._original(character)
		             .build();
	}


}
