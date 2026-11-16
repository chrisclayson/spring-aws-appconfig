package uk.org.clayson.spring.appconfig.refresh;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;

import uk.org.clayson.spring.appconfig.factory.AppConfigClientFactory;
import uk.org.clayson.spring.appconfig.model.AppConfigContent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Proves that Spring Cloud's real {@code ContextRefresher} rebinds
 * {@code @ConfigurationProperties} after {@link AppConfigRefresher} updates the property
 * source.
 */
class AppConfigRefreshIntegrationTest {

	@Test
	void refreshesConfigurationPropertiesWhenAppConfigChanges() {
		AppConfigContent initial = new AppConfigContent("greeting.message: Hello", "application/x-yaml", "1");
		AppConfigContent updated = new AppConfigContent("greeting.message: Updated", "application/x-yaml", "2");
		AtomicInteger clients = new AtomicInteger();

		SpringApplication application = new SpringApplication(RefreshTestApplication.class);
		application.setWebApplicationType(WebApplicationType.NONE);
		application.setBannerMode(Banner.Mode.OFF);
		application.addBootstrapRegistryInitializer((registry) -> {
			AppConfigClientFactory factory = (connectionDetails) -> {
				if (clients.incrementAndGet() == 1) {
					return () -> initial;
				}
				AtomicBoolean firstFetch = new AtomicBoolean(true);
				return () -> firstFetch.getAndSet(false) ? updated : new AppConfigContent("", null, null);
			};
			registry.register(AppConfigClientFactory.class, (bootstrapContext) -> factory);
		});

		try (ConfigurableApplicationContext context = application.run(
				"--spring.config.import=aws-appconfig:demo-app/demo", "--appconfig.refresh.interval=50ms")) {
			await().atMost(Duration.ofSeconds(10))
				.untilAsserted(() -> assertThat(context.getBean(GreetingProperties.class).getMessage())
					.isEqualTo("Updated"));
		}
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@EnableConfigurationProperties(GreetingProperties.class)
	static class RefreshTestApplication {

	}

	@ConfigurationProperties(prefix = "greeting")
	static class GreetingProperties {

		private String message;

		String getMessage() {
			return this.message;
		}

		void setMessage(String message) {
			this.message = message;
		}

	}

}
