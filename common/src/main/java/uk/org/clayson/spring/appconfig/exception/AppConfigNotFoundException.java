package uk.org.clayson.spring.appconfig.exception;

/**
 * Thrown when the requested AWS AppConfig application, environment or configuration
 * profile does not exist (or is not visible with the current credentials).
 */
public class AppConfigNotFoundException extends AppConfigException {

	public AppConfigNotFoundException(String message) {
		super(message);
	}

	public AppConfigNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

}
