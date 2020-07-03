package io.rsocket.examples.service.smith.notifier;

import io.rsocket.examples.common.model.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class Notifier {

	final WebClient webClient;

	public Mono<Void> sendNotification(Notification notification) {
		return webClient.post()
		                .uri("/letter-status")
		                .body(BodyInserters.fromValue(notification))
		                .retrieve()
		                .bodyToMono(Void.class)
		                .doOnSuccess(__ -> log.info("Guard notification sent"))
		                .doOnError(e -> log.error("no sender url found", e));
	}
}
