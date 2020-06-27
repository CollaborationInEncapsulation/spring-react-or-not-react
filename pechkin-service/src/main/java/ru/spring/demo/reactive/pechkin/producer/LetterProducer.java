package ru.spring.demo.reactive.pechkin.producer;

import com.github.javafaker.RickAndMorty;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import ru.spring.demo.reactive.starter.speed.model.Letter;

import org.springframework.stereotype.Service;

/**
 * @author Evgeny Borisov
 */
@Slf4j
@Service
@Setter
public class LetterProducer {

	final RickAndMorty faker;
	final Counter      counter;

	public LetterProducer(RickAndMorty faker, MeterRegistry meterRegistry) {
		this.faker = faker;
		this.counter = meterRegistry.counter("letter.rps");
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
