package ru.spring.demo.reactive.bigbro.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.pool.InstrumentedPool;
import ru.spring.demo.reactive.bigbro.services.GuardService;
import ru.spring.demo.reactive.bigbro.services.LetterDecoder;
import ru.spring.demo.reactive.starter.speed.model.Letter;
import ru.spring.demo.reactive.starter.speed.support.Worker;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/analyse/letter")
@RequiredArgsConstructor
public class LetterController {

	final InstrumentedPool<Worker> pool;
	final LetterDecoder            decoder;
	final GuardService             guardService;

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public Mono<ResponseEntity<Void>> processLetter(@RequestBody Letter letter) {
		return pool.withPoolable(worker -> worker.execute(() -> decoder
			           .decode(letter)
			           .flatMap(guardService::send)
				   ))
		           .then(Mono.just(ResponseEntity.accepted().<Void>build()))
		           .onErrorReturn(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build());
	}

}
