package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * Schema changes live in Flyway migrations (src/main/resources/db/migration), not in startup code.
 * The previous startup "schema updater" here is replaced by V1_1__catch_up_legacy_schema.sql; its
 * "data cleanup" step, which overwrote uploaded product images larger than ~100 KB with a stock photo on
 * every restart, was removed.
 *
 * UserDetailsServiceAutoConfiguration is excluded because logins are handled by AuthService; otherwise
 * Spring Boot would create an unused in-memory "user" account with a generated password.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}
}
