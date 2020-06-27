package ru.spring.demo.reactive.starter.speed;

import java.time.Duration;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Evgeny Borisov
 */
@Data
@ConfigurationProperties("letter")
public class AdjustmentProperties {

	private final ReceiverProperties   receiver   = new ReceiverProperties();
	private final SenderProperties     sender     = new SenderProperties();
	private final ProcessingProperties processing = new ProcessingProperties();

	public enum ErrorStrategy {
		DROP, RETRY, FAIL
	}

	public enum OverflowStrategy {
		BLOCK,
		ERROR,
		TERMINATE
	}

	@Data
	public static class SenderProperties {

		private final RateLimitProperties rateLimit = new RateLimitProperties();

		private ErrorStrategy errorStrategy = ErrorStrategy.FAIL;
	}

	@Data
	public static class RateLimitProperties {

		boolean  enabled     = false;
		int      limit       = 1;
		Duration period      = Duration.ofSeconds(1);
		boolean  distributed = false;
	}

	@Data
	public static class ProcessingProperties {

		int              queueSize        = 100;
		OverflowStrategy overflowStrategy = OverflowStrategy.TERMINATE;
		int              concurrencyLevel = 1;
		int              time             = 500;
		int              randomDelay      = 0;

	}

	@Data
	public static class ReceiverProperties {

		private String baseUrl;
	}
}
