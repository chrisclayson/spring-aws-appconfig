package uk.org.clayson.spring.appconfig.configdata;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import uk.org.clayson.spring.appconfig.exception.AppConfigException;
import uk.org.clayson.spring.appconfig.exception.AppConfigNotFoundException;
import uk.org.clayson.spring.appconfig.factory.AppConfigClientFactory;
import uk.org.clayson.spring.appconfig.model.AppConfigConnectionDetails;
import uk.org.clayson.spring.appconfig.model.AppConfigContent;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots a real {@link SpringApplication} so the complete config-data pipeline
 * (spring.factories, resolver, loader and property source insertion) is exercised. AWS
 * is replaced with a fake {@link AppConfigClientFactory} registered in the bootstrap
 * registry.
 */
class AppConfigConfigDataIntegrationTest {

	private final List<AppConfigConnectionDetails> requested = new CopyOnWriteArrayList<>();

	private AppConfigContent content = new AppConfigContent("", null, null);

	private RuntimeException failure;

	private ConfigurableApplicationContext context;

	@AfterEach
	void closeContext() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	void loadsConfigurationAndOverridesLocalApplicationProperties() {
		this.content = new AppConfigContent("""
				greeting:
				  message: Hello from AppConfig
				demo:
				  override: from-appconfig
				""", "application/x-yaml", "7");

		this.context = run("--spring.config.import=aws-appconfig:demo-app/prod/demo-config");

		Environment environment = this.context.getEnvironment();
		assertThat(environment.getProperty("greeting.message")).isEqualTo("Hello from AppConfig");
		assertThat(environment.getProperty("demo.override")).isEqualTo("from-appconfig");
		assertThat(this.requested).singleElement().satisfies((details) -> {
			assertThat(details.application()).isEqualTo("demo-app");
			assertThat(details.environment()).isEqualTo("prod");
			assertThat(details.configurationProfile()).isEqualTo("demo-config");
			assertThat(details.contentType()).isNull();
		});
	}

	@Test
	void usesAppConfigContentTypeHeaderWhenNotConfigured() {
		this.content = new AppConfigContent("greeting.message=Hello", "text/plain", "1");

		this.context = run("--spring.config.import=aws-appconfig:demo-app");

		assertThat(this.context.getEnvironment().getProperty("greeting.message")).isEqualTo("Hello");
	}

	@Test
	void appliesConfiguredPrefix() {
		this.content = new AppConfigContent("greeting:\n  message: Hello\n", "application/x-yaml", "1");

		this.context = run("--spring.config.import=aws-appconfig:demo-app", "--appconfig.prefix=demo");

		assertThat(this.context.getEnvironment().getProperty("demo.greeting.message")).isEqualTo("Hello");
		assertThat(this.context.getEnvironment().getProperty("greeting.message")).isNull();
	}

	@Test
	void usesBootstrapPropertiesForMissingLocationSegments() {
		this.content = new AppConfigContent("greeting:\n  message: Hello\n", "application/x-yaml", "1");

		this.context = run("--spring.config.import=aws-appconfig:demo-app", "--appconfig.environment=staging",
				"--appconfig.profile=demo-api");

		assertThat(this.requested).singleElement().satisfies((details) -> {
			assertThat(details.application()).isEqualTo("demo-app");
			assertThat(details.environment()).isEqualTo("staging");
			assertThat(details.configurationProfile()).isEqualTo("demo-api");
		});
	}

	@Test
	void optionalLocationIsIgnoredWhenConfigurationDoesNotExist() {
		this.failure = new AppConfigNotFoundException("not found");

		this.context = run("--spring.config.import=optional:aws-appconfig:missing-app");

		assertThat(this.context.getEnvironment().getProperty("greeting.message")).isNull();
		assertThat(this.requested).isNotEmpty()
			.allSatisfy((details) -> assertThat(details.application()).isEqualTo("missing-app"));
	}

	@Test
	void failureCanBeDowngradedToAWarning() {
		this.failure = new AppConfigException("boom");

		this.context = run("--spring.config.import=aws-appconfig:demo-app", "--appconfig.fail-fast=false");

		assertThat(this.context.getEnvironment().getProperty("greeting.message")).isNull();
	}

	private ConfigurableApplicationContext run(String... args) {
		SpringApplication application = new SpringApplication(TestApplication.class);
		application.setWebApplicationType(WebApplicationType.NONE);
		application.setBannerMode(Banner.Mode.OFF);
		application.addBootstrapRegistryInitializer((registry) -> {
			AppConfigClientFactory factory = (details) -> {
				this.requested.add(details);
				if (this.failure != null) {
					RuntimeException failureToThrow = this.failure;
					return () -> {
						throw failureToThrow;
					};
				}
				AppConfigContent contentToReturn = this.content;
				return () -> contentToReturn;
			};
			registry.register(AppConfigClientFactory.class, (bootstrapContext) -> factory);
		});
		return application.run(args);
	}

	@SpringBootConfiguration
	static class TestApplication {

	}

}
