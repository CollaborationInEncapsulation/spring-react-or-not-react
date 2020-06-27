package ru.spring.demo.reactive.bigbro.services;

import java.util.function.Function;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import ru.spring.demo.reactive.starter.speed.model.DecodedLetter;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class GuardService {

	final Function<Mono<Void>, Mono<Void>> responseHandlerFunction;
	final WebClient                        webClient;

	public Mono<Void> send(DecodedLetter decodedLetter) {
		GuardRequest request = new GuardRequest().setLetterId(decodedLetter.getAuthor())
		                                         .setMessage(decodedLetter.getContent());

		return webClient
			.post()
			.uri("/guard")
			.body(BodyInserters.fromValue(request))
			.retrieve()
			.bodyToMono(Void.class)
			.transform(responseHandlerFunction);
	}

	@Data
	@Accessors(chain = true)
	@NoArgsConstructor
	public static class GuardRequest {

		private String letterId;
		private String message;
	}
}