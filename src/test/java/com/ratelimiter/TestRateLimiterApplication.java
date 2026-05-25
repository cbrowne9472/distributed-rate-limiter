package com.ratelimiter;

import org.springframework.boot.SpringApplication;

public class TestRateLimiterApplication {

	public static void main(String[] args) {
		SpringApplication.from(RateLimiterApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
