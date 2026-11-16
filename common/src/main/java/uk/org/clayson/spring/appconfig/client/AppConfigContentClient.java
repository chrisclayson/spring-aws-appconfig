package uk.org.clayson.spring.appconfig.client;

import uk.org.clayson.spring.appconfig.exception.AppConfigException;
import uk.org.clayson.spring.appconfig.exception.AppConfigNotFoundException;
import uk.org.clayson.spring.appconfig.model.AppConfigContent;

/**
 * Fetches the latest configuration payload from AWS AppConfig.
 * <p>
 * Implementations are created by an {@code AppConfigClientFactory}. A client is normally
 * used for a single bootstrap fetch, but implementations may be polled repeatedly since
 * they own the AppConfig session token.
 */
@FunctionalInterface
public interface AppConfigContentClient extends AutoCloseable {

	/**
	 * Fetch the latest configuration.
	 * @return the configuration content
	 * @throws AppConfigNotFoundException if the configuration does not exist
	 * @throws AppConfigException if the configuration cannot be read
	 */
	AppConfigContent fetch();

	@Override
	default void close() {
	}

}
