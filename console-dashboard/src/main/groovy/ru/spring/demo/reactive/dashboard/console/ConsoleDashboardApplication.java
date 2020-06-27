package ru.spring.demo.reactive.dashboard.console;

import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ConsoleDashboardApplication {

    public static void main(String[] args) {
        System.setProperty("jansi.passthrough", "true");
        new SpringApplicationBuilder(ConsoleDashboardApplication.class)
                .headless(true)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .run(args);
    }
}
