package ru.spring.demo.reactive.smith.controller;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.pool.InstrumentedPool;
import ru.spring.demo.reactive.smith.decider.GuardDecider;
import ru.spring.demo.reactive.starter.speed.model.DecodedLetter;
import ru.spring.demo.reactive.starter.speed.support.Worker;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DecodedLetterController {

	final GuardDecider             decider;
	final InstrumentedPool<Worker> pool;

	@PostMapping(value = "/guard", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Mono<ResponseEntity<Void>> updateLetterStatus(@RequestBody DecodedLetter decodedLetter) {
		return pool.withPoolable(worker -> worker.execute(() -> decider.decide(decodedLetter)))
		           .then(Mono.just(ResponseEntity.accepted().<Void>build()))
		           .onErrorReturn(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build());
	}

}