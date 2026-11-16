package uk.org.clayson.spring.appconfig.factory;

import uk.org.clayson.spring.appconfig.client.AppConfigContentClient;
import uk.org.clayson.spring.appconfig.model.AppConfigConnectionDetails;

/**
 * Creates an {@link AppConfigContentClient} for a resolved set of connection details.
 * <p>
 * The default implementation uses the AWS SDK v2 data-plane client. Applications can
 * register their own factory in the Spring Boot {@code BootstrapRegistry} to customise
 * credentials, use a local endpoint, or provide a fake in tests.
 */
@FunctionalInterface
public interface AppConfigClientFactory {

	AppConfigContentClient create(AppConfigConnectionDetails connectionDetails);

}
