package uk.org.clayson.spring.appconfig.model;

import java.util.Locale;

/**
 * The formats that can be used for a freeform AWS AppConfig hosted configuration.
 * <p>
 * JSON content is parsed with the YAML loader because JSON is a subset of YAML; this also
 * avoids an extra Jackson dependency.
 */
public enum AppConfigContentType {

	YAML("yaml"),

	JSON("json"),

	PROPERTIES("properties");

	private final String extension;

	AppConfigContentType(String extension) {
		this.extension = extension;
	}

	/**
	 * Return the file extension used when the content is parsed.
	 * @return the file extension (without a leading dot)
	 */
	public String extension() {
		return this.extension;
	}

	/**
	 * Parse an explicitly configured content type, for example
	 * {@code appconfig.content-type=properties}.
	 * @param value the configured value (may be {@code null})
	 * @return the content type, or {@code null} when no value was configured
	 * @throws IllegalArgumentException if the value is not supported
	 */
	public static AppConfigContentType from(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "yaml", "yml", "application/x-yaml", "application/yaml", "text/yaml" -> YAML;
			case "json", "application/json" -> JSON;
			case "properties", "application/x-java-properties", "text/properties", "text/plain" -> PROPERTIES;
			default -> throw new IllegalArgumentException("Unsupported AWS AppConfig content type '" + value
					+ "'. Supported values are: yaml, json, properties");
		};
	}

	/**
	 * Best-effort detection based on the {@code Content-Type} returned by AWS AppConfig.
	 * @param contentType the content type returned by AWS AppConfig (may be {@code null})
	 * @param fallback the type to use when the content type is missing or unknown
	 * @return the detected content type
	 */
	public static AppConfigContentType detect(String contentType, AppConfigContentType fallback) {
		if (contentType == null || contentType.isBlank()) {
			return fallback;
		}
		String normalized = contentType.toLowerCase(Locale.ROOT);
		if (normalized.contains("json")) {
			return JSON;
		}
		if (normalized.contains("yaml") || normalized.contains("yml")) {
			return YAML;
		}
		if (normalized.contains("properties")) {
			return PROPERTIES;
		}
		if (normalized.startsWith("text/plain")) {
			// AppConfig's conventional content type for flat key/value freeform data.
			return PROPERTIES;
		}
		return fallback;
	}

}
