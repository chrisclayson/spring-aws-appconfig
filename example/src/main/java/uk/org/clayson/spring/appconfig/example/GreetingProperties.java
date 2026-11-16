package uk.org.clayson.spring.appconfig.example;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties that are expected to come from AWS AppConfig.
 */
@ConfigurationProperties(prefix = "greeting")
public class GreetingProperties {

	private String message;

	private String source;

	public String getMessage() {
		return this.message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getSource() {
		return this.source;
	}

	public void setSource(String source) {
		this.source = source;
	}

}
