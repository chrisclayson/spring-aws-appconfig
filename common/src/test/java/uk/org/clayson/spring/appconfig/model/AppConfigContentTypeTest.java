package uk.org.clayson.spring.appconfig.model;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AppConfigContentTypeTest {

	@Test
	void parsesSupportedPropertyValues() {
		assertThat(AppConfigContentType.from("yaml")).isEqualTo(AppConfigContentType.YAML);
		assertThat(AppConfigContentType.from("YML")).isEqualTo(AppConfigContentType.YAML);
		assertThat(AppConfigContentType.from("application/json")).isEqualTo(AppConfigContentType.JSON);
		assertThat(AppConfigContentType.from("properties")).isEqualTo(AppConfigContentType.PROPERTIES);
		assertThat(AppConfigContentType.from(" ")).isNull();
	}

	@Test
	void rejectsUnsupportedPropertyValues() {
		assertThatIllegalArgumentException().isThrownBy(() -> AppConfigContentType.from("xml"))
			.withMessageContaining("Unsupported AWS AppConfig content type");
	}

	@Test
	void detectsContentTypeFromResponse() {
		assertThat(AppConfigContentType.detect("application/json", AppConfigContentType.YAML))
			.isEqualTo(AppConfigContentType.JSON);
		assertThat(AppConfigContentType.detect("application/x-yaml", AppConfigContentType.JSON))
			.isEqualTo(AppConfigContentType.YAML);
		assertThat(AppConfigContentType.detect("text/plain", AppConfigContentType.YAML))
			.isEqualTo(AppConfigContentType.PROPERTIES);
		assertThat(AppConfigContentType.detect(null, AppConfigContentType.YAML))
			.isEqualTo(AppConfigContentType.YAML);
	}

	@Test
	void connectionDetailsRequireAValidPollInterval() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new AppConfigConnectionDetails("app", "env", "profile", null, null, null,
					Duration.ofSeconds(14), null, true))
			.withMessageContaining("at least 15 seconds");
	}

}
