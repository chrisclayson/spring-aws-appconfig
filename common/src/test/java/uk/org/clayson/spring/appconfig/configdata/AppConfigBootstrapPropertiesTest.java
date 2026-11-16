package uk.org.clayson.spring.appconfig.configdata;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import uk.org.clayson.spring.appconfig.model.AppConfigConnectionDetails;
import uk.org.clayson.spring.appconfig.model.AppConfigContentType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AppConfigBootstrapPropertiesTest {

	@Test
	void usesBootstrapPropertiesWhenLocationHasNoSegments() {
		AppConfigBootstrapProperties properties = new AppConfigBootstrapProperties();
		properties.setApplication("orders");
		properties.setEnvironment("prod");
		properties.setProfile("orders-api");
		properties.setRegion("eu-west-1");
		properties.setContentType("properties");
		properties.setPrefix("orders.");
		properties.setMinimumPollInterval(Duration.ofSeconds(30));
		properties.setFailFast(false);

		AppConfigConnectionDetails details = properties.toConnectionDetails("");

		assertThat(details.application()).isEqualTo("orders");
		assertThat(details.environment()).isEqualTo("prod");
		assertThat(details.configurationProfile()).isEqualTo("orders-api");
		assertThat(details.region()).isEqualTo("eu-west-1");
		assertThat(details.contentType()).isEqualTo(AppConfigContentType.PROPERTIES);
		assertThat(details.prefix()).isEqualTo("orders");
		assertThat(details.minimumPollInterval()).isEqualTo(Duration.ofSeconds(30));
		assertThat(details.failFast()).isFalse();
	}

	@Test
	void locationSegmentsOverrideBootstrapProperties() {
		AppConfigBootstrapProperties properties = new AppConfigBootstrapProperties();
		properties.setApplication("ignored");
		properties.setEnvironment("ignored");
		properties.setProfile("ignored");

		AppConfigConnectionDetails details = properties.toConnectionDetails("/checkout/staging/checkout-api/");

		assertThat(details.application()).isEqualTo("checkout");
		assertThat(details.environment()).isEqualTo("staging");
		assertThat(details.configurationProfile()).isEqualTo("checkout-api");
	}

	@Test
	void emptyLocationSegmentsFallBackToDefaults() {
		AppConfigBootstrapProperties properties = new AppConfigBootstrapProperties();

		AppConfigConnectionDetails details = properties.toConnectionDetails("checkout//checkout-api");

		assertThat(details.application()).isEqualTo("checkout");
		assertThat(details.environment()).isEqualTo("default");
		assertThat(details.configurationProfile()).isEqualTo("checkout-api");
		assertThat(details.contentType()).isNull();
		assertThat(details.minimumPollInterval()).isEqualTo(Duration.ofSeconds(15));
		assertThat(details.failFast()).isTrue();
	}

	@Test
	void configurationProfileDefaultsToApplicationName() {
		AppConfigBootstrapProperties properties = new AppConfigBootstrapProperties();

		AppConfigConnectionDetails details = properties.toConnectionDetails("checkout/prod");

		assertThat(details.configurationProfile()).isEqualTo("checkout");
	}

	@Test
	void applicationNameIsRequired() {
		AppConfigBootstrapProperties properties = new AppConfigBootstrapProperties();

		assertThatIllegalArgumentException().isThrownBy(() -> properties.toConnectionDetails(""))
			.withMessageContaining("application name is required");
	}

	@Test
	void rejectsLocationsWithTooManySegments() {
		AppConfigBootstrapProperties properties = new AppConfigBootstrapProperties();

		assertThatIllegalArgumentException().isThrownBy(() -> properties.toConnectionDetails("a/b/c/d"))
			.withMessageContaining("Invalid AWS AppConfig location");
	}

}
