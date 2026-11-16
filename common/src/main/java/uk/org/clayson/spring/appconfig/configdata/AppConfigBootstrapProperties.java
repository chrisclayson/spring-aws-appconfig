package uk.org.clayson.spring.appconfig.configdata;

import java.net.URI;
import java.time.Duration;

import org.springframework.util.StringUtils;

import uk.org.clayson.spring.appconfig.model.AppConfigConnectionDetails;
import uk.org.clayson.spring.appconfig.model.AppConfigContentType;

/**
 * Binds the {@code appconfig.*} bootstrap properties. These values configure how the
 * AppConfig location is resolved; they are read before any configuration data is loaded
 * and can therefore be supplied as environment variables, system properties or command
 * line arguments (or in {@code application.properties} when one is present).
 */
final class AppConfigBootstrapProperties {

	private String application;

	private String environment;

	private String profile;

	private String region;

	private String endpoint;

	private String contentType;

	private Duration minimumPollInterval;

	private String prefix;

	private Boolean failFast;

	AppConfigConnectionDetails toConnectionDetails(String locationValue) {
		String[] segments = split(locationValue);
		String application = segment(segments, 0, this.application);
		if (!StringUtils.hasText(application)) {
			throw new IllegalArgumentException(
					"AWS AppConfig application name is required. Set 'appconfig.application' or use "
							+ "'spring.config.import=aws-appconfig:<application>/<environment>/<configuration-profile>'");
		}
		String environment = segment(segments, 1, defaultIfBlank(this.environment, "default"));
		String profile = segment(segments, 2, this.profile);
		if (!StringUtils.hasText(profile)) {
			profile = application;
		}
		URI endpointUri = StringUtils.hasText(this.endpoint) ? URI.create(this.endpoint.trim()) : null;
		return new AppConfigConnectionDetails(application, environment, profile, trimToNull(this.region), endpointUri,
				AppConfigContentType.from(this.contentType), this.minimumPollInterval, this.prefix,
				this.failFast == null || this.failFast);
	}

	private static String[] split(String locationValue) {
		String value = (locationValue != null) ? locationValue.trim() : "";
		while (value.startsWith("/")) {
			value = value.substring(1);
		}
		while (value.endsWith("/")) {
			value = value.substring(0, value.length() - 1);
		}
		if (value.isEmpty()) {
			return new String[0];
		}
		String[] segments = value.split("/", -1);
		if (segments.length > 3) {
			throw new IllegalArgumentException("Invalid AWS AppConfig location '" + locationValue
					+ "'. Expected '<application>[/<environment>[/<configuration-profile>]]'");
		}
		return segments;
	}

	private static String segment(String[] segments, int index, String fallback) {
		if (index < segments.length && StringUtils.hasText(segments[index])) {
			return segments[index].trim();
		}
		return fallback;
	}

	private static String defaultIfBlank(String value, String defaultValue) {
		return StringUtils.hasText(value) ? value.trim() : defaultValue;
	}

	private static String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	public String getApplication() {
		return this.application;
	}

	public void setApplication(String application) {
		this.application = application;
	}

	public String getEnvironment() {
		return this.environment;
	}

	public void setEnvironment(String environment) {
		this.environment = environment;
	}

	public String getProfile() {
		return this.profile;
	}

	public void setProfile(String profile) {
		this.profile = profile;
	}

	public String getRegion() {
		return this.region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public String getEndpoint() {
		return this.endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public String getContentType() {
		return this.contentType;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	public Duration getMinimumPollInterval() {
		return this.minimumPollInterval;
	}

	public void setMinimumPollInterval(Duration minimumPollInterval) {
		this.minimumPollInterval = minimumPollInterval;
	}

	public String getPrefix() {
		return this.prefix;
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	public Boolean getFailFast() {
		return this.failFast;
	}

	public void setFailFast(Boolean failFast) {
		this.failFast = failFast;
	}

}
