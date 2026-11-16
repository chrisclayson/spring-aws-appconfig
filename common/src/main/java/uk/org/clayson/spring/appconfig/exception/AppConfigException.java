package uk.org.clayson.spring.appconfig.exception;

/**
 * Thrown when configuration cannot be read from AWS AppConfig.
 */
public class AppConfigException extends RuntimeException {

	public AppConfigException(String message) {
		super(message);
	}

	public AppConfigException(String message, Throwable cause) {
		super(message, cause);
	}

}
