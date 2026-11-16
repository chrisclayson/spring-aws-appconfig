package uk.org.clayson.spring.appconfig.property;

import java.util.Map;

import org.junit.jupiter.api.Test;

import uk.org.clayson.spring.appconfig.model.AppConfigContent;
import uk.org.clayson.spring.appconfig.model.AppConfigContentType;

import static org.assertj.core.api.Assertions.assertThat;

class AppConfigPropertySourceLoaderTest {

	private final AppConfigPropertySourceLoader loader = new AppConfigPropertySourceLoader();

	@Test
	void loadsYamlContent() throws Exception {
		AppConfigContent content = new AppConfigContent("""
				greeting:
				  message: Hello
				server:
				  port: 8080
				""", "application/x-yaml", "1");

		Map<String, Object> properties = this.loader.load("test", content, AppConfigContentType.YAML, null);

		assertThat(properties).containsEntry("greeting.message", "Hello").containsEntry("server.port", 8080);
	}

	@Test
	void loadsJsonContent() throws Exception {
		AppConfigContent content = new AppConfigContent("""
				{"greeting": {"message": "Hello"}, "server": {"port": 8080}}
				""", "application/json", "1");

		Map<String, Object> properties = this.loader.load("test", content, AppConfigContentType.JSON, null);

		assertThat(properties).containsEntry("greeting.message", "Hello").containsEntry("server.port", 8080);
	}

	@Test
	void loadsPropertiesContent() throws Exception {
		AppConfigContent content = new AppConfigContent("greeting.message=Hello\nserver.port=8080\n", "text/plain",
				"1");

		Map<String, Object> properties = this.loader.load("test", content, AppConfigContentType.PROPERTIES, null);

		assertThat(properties).containsEntry("greeting.message", "Hello").containsEntry("server.port", "8080");
	}

	@Test
	void appliesPrefixToAllPropertyNames() throws Exception {
		AppConfigContent content = new AppConfigContent("greeting:\n  message: Hello\n", "application/x-yaml", "1");

		Map<String, Object> properties = this.loader.load("test", content, AppConfigContentType.YAML, "demo");

		assertThat(properties).containsOnlyKeys("demo.greeting.message");
		assertThat(properties).containsEntry("demo.greeting.message", "Hello");
	}

	@Test
	void returnsNoPropertiesForEmptyContent() throws Exception {
		AppConfigContent content = new AppConfigContent("", "application/x-yaml", "1");

		assertThat(this.loader.load("test", content, AppConfigContentType.YAML, null)).isEmpty();
	}

}
