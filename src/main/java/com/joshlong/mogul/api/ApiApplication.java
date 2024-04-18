package com.joshlong.mogul.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.IntegrationComponentScan;

import java.time.format.DateTimeFormatter;

@IntegrationComponentScan
@EnableConfigurationProperties(ApiProperties.class)
@SpringBootApplication
public class ApiApplication {

	public static void main(String[] args) {

		System.getenv().forEach((k, v) -> System.out.println("\t" + k + '=' + v));

		SpringApplication.run(ApiApplication.class, args);
	}

	@Bean
	DateTimeFormatter dateTimeFormatter() {
		return DateTimeFormatter.BASIC_ISO_DATE;
	}

}
