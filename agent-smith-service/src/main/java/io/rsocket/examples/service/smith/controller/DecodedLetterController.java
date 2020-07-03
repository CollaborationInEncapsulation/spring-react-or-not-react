package io.rsocket.examples.service.smith.controller;

import io.rsocket.examples.common.control.Worker;
import io.rsocket.examples.common.model.DecodedLetter;
import io.rsocket.examples.service.smith.decider.GuardDecider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.pool.InstrumentedPool;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class DecodedLetterController {

	final GuardDecider             decider;
	final InstrumentedPool<Worker> pool;

	@PostMapping(value = "/guard", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Mono<ResponseEntity<Void>> updateLetterStatus(@RequestBody DecodedLetter decodedLetter) {
		log.info("Received DecodedLetter={} for analysis", decodedLetter);

		return pool.withPoolable(worker -> worker.execute(() -> decider.decide(decodedLetter)))
		           .then(Mono.just(ResponseEntity.accepted().<Void>build()))
		           .onErrorReturn(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build());
	}

}