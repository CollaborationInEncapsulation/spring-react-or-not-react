package io.rsocket.examples.starter.metrics;

import java.util.concurrent.atomic.AtomicBoolean;

import com.codahale.metrics.MetricRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.dropwizard.DropwizardConfig;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import reactor.pool.InstrumentedPool;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.rsocket.RSocketRequester;

@Configuration
@AutoConfigureBefore(SimpleMetricsExportAutoConfiguration.class)
public class RatesMetricsConfiguration {

	@Bean
	public MetricRegistry dropwizardRegistry() {
		return new MetricRegistry();
	}

	@Bean
	public MeterRegistry consoleLoggingRegistry(MetricRegistry dropwizardRegistry) {
		DropwizardConfig consoleConfig = new DropwizardConfig() {

			@Override
			public String prefix() {
				return "console";
			}

			@Override
			public String get(String key) {
				return null;
			}

		};

		return new DropwizardMeterRegistry(consoleConfig,
				dropwizardRegistry,
				HierarchicalNameMapper.DEFAULT,
				Clock.SYSTEM) {
			@Override
			protected Double nullGaugeValue() {
				return null;
			}
		};
	}

	@Bean("letter.status")
	public AtomicBoolean letterStatusReporter() {
		return new AtomicBoolean(true);
	}

	@Bean
	public RatesMetricsReporter ratesMetricsEndpoint(MetricRegistry m,
			InstrumentedPool<?> pool,
			RSocketRequester.Builder builder,
			@Qualifier("letter.status") AtomicBoolean status,
			@Value("${spring.application.order}") int order,
			@Value("${spring.application.name}") String serviceName) {
		return new RatesMetricsReporter(m, pool, builder, status, serviceName, order);
	}
}
