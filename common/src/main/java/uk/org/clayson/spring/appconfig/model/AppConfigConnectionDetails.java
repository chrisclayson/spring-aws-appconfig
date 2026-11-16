package uk.org.clayson.spring.appconfig.model;

import java.net.URI;
import java.time.Duration;

import org.springframework.util.StringUtils;

/**
 * Fully resolved details needed to read a configuration from AWS AppConfig.
 *
 * @param application the AppConfig application name
 * @param environment the AppConfig environment name
 * @param configurationProfile the AppConfig configuration profile name
 * @param region the AWS region, or {@code null} to use the SDK default region provider
 * chain
 * @param endpoint a custom endpoint (for example LocalStack), or {@code null}
 * @param contentType the configured content type, or {@code null} to detect it from the
 * response
 * @param minimumPollInterval the minimum poll interval sent when starting a session
 * @param prefix an optional prefix applied to every property name, or {@code null}
 * @param failFast whether a failure to read the configuration should stop startup
 */
public record AppConfigConnectionDetails(String application, String environment, String configurationProfile,
		String region, URI endpoint, AppConfigContentType contentType, Duration minimumPollInterval, String prefix,
		boolean failFast) {

	/** The smallest poll interval accepted by the AWS AppConfig data plane. */
	public static final Duration MINIMUM_POLL_INTERVAL = Duration.ofSeconds(15);

	public AppConfigConnectionDetails {
		application = requireText(application, "application");
		environment = requireText(environment, "environment");
		configurationProfile = requireText(configurationProfile, "configurationProfile");
		minimumPollInterval = (minimumPollInterval != null) ? minimumPollInterval : MINIMUM_POLL_INTERVAL;
		if (minimumPollInterval.compareTo(MINIMUM_POLL_INTERVAL) < 0) {
			throw new IllegalArgumentException(
					"'minimumPollInterval' must be at least " + MINIMUM_POLL_INTERVAL.toSeconds() + " seconds");
		}
		prefix = normalizePrefix(prefix);
	}

	/**
	 * Return a human-readable description of the configuration being read.
	 * @return the description
	 */
	public String description() {
		return this.application + "/" + this.environment + "/" + this.configurationProfile;
	}

	private static String requireText(String value, String name) {
		if (!StringUtils.hasText(value)) {
			throw new IllegalArgumentException("AWS AppConfig " + name + " must not be empty");
		}
		return value.trim();
	}

	private static String normalizePrefix(String prefix) {
		if (!StringUtils.hasText(prefix)) {
			return null;
		}
		String normalized = prefix.trim();
		while (normalized.startsWith(".")) {
			normalized = normalized.substring(1);
		}
		while (normalized.endsWith(".")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		return normalized.isEmpty() ? null : normalized;
	}

}
