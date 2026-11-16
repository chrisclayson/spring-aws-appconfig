package uk.org.clayson.spring.appconfig.example;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import uk.org.clayson.spring.appconfig.factory.AppConfigClientFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test for the example application. By default it provisions the configuration
 * in AWS Emulator; set {@code APPCONFIG_IT_MODE=real} and provide AWS credentials to run
 * the same flow against the real service.
 */
class AppConfigIntegrationTest {

	@Test
	void readsConfigurationFromAppConfig() throws Exception {
		try (AppConfigTestEnvironment environment = AppConfigTestEnvironment.start()) {
			String source = environment.mode();
			String message = "Hello from " + source + " AppConfig";
			environment.publishConfiguration("""
					greeting:
					  message: %s
					  source: %s
					""".formatted(message, source));

			SpringApplication application = new SpringApplication(ExampleApplication.class);
			application.setWebApplicationType(WebApplicationType.NONE);
			application.setBannerMode(Banner.Mode.OFF);
			AppConfigClientFactory clientFactory = environment.clientFactory();
			if (clientFactory != null) {
				application.addBootstrapRegistryInitializer((registry) -> registry
					.register(AppConfigClientFactory.class, (bootstrapContext) -> clientFactory));
			}

			List<String> arguments = new ArrayList<>();
			arguments.add("--spring.config.import=aws-appconfig:" + environment.location());
			arguments.add("--appconfig.region=" + environment.region().id());
			arguments.add("--appconfig.content-type=yaml");
			if (environment.endpoint() != null) {
				arguments.add("--appconfig.endpoint=" + environment.endpoint());
			}

			try (ConfigurableApplicationContext context = application
				.run(arguments.toArray(String[]::new))) {
				GreetingProperties greeting = context.getBean(GreetingProperties.class);
				assertThat(greeting.getMessage()).isEqualTo(message);
				assertThat(greeting.getSource()).isEqualTo(source);
				assertThat(context.getEnvironment().getProperty("greeting.message")).isEqualTo(message);
				assertThat(context.getEnvironment().getProperty("greeting.source")).isEqualTo(source);
			}
		}
	}

}
