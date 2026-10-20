package com.campus.canteen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Main application entry point for CampusBite — Campus Canteen Ordering System.
 *
 * <p>Bootstraps the Spring Boot application, initializing component scanning,
 * auto-configuration, and the embedded Tomcat server.
 */
@SpringBootApplication
public class CanteenApplication {

	/**
	 * Application execution entry point.
	 *
	 * @param args command-line arguments passed to the application
	 */
	public static void main(String[] args) {
		SpringApplication.run(CanteenApplication.class, args);
	}

}
