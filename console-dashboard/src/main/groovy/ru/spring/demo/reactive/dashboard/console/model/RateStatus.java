package ru.spring.demo.reactive.dashboard.console.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RateStatus {

	private String component;
	private String status; // indicates whether UP or DOWN
	private int    order;
	private double letterRps;
	private double letterFps; // failure per second
	private double letterDps; // drops per second

	@JsonProperty("buffer.size")
	private int bufferSize;

	@JsonProperty("buffer.capacity")
	private int bufferCapacity;

	private List<BufferStatus> buffers;

	@Data
	public static class BufferStatus {

		private int remaining;
		private int maxSize;
		private int activeWorker;
		private int workersCount;
	}
}
