package uk.org.clayson.spring.appconfig.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.ApplicationRunner;

/**
 * Minimal example application whose configuration comes from AWS AppConfig.
 */
@SpringBootApplication
@EnableConfigurationProperties(GreetingProperties.class)
public class ExampleApplication {

	public static void main(String[] args) {
		SpringApplication.run(ExampleApplication.class, args);
	}

	@Bean
	ApplicationRunner logGreeting(GreetingProperties greeting) {
		return (args) -> System.out.println(
				"Greeting from AppConfig: " + greeting.getMessage() + " (source: " + greeting.getSource() + ")");
	}

}
