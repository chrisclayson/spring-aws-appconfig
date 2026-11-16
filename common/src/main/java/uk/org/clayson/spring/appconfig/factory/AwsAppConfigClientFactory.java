package uk.org.clayson.spring.appconfig.factory;

import uk.org.clayson.spring.appconfig.client.AppConfigContentClient;
import uk.org.clayson.spring.appconfig.client.AwsAppConfigContentClient;
import uk.org.clayson.spring.appconfig.model.AppConfigConnectionDetails;

/**
 * Default {@link AppConfigClientFactory} backed by the AWS SDK v2 AppConfig data plane
 * client.
 */
public final class AwsAppConfigClientFactory implements AppConfigClientFactory {

	@Override
	public AppConfigContentClient create(AppConfigConnectionDetails connectionDetails) {
		return new AwsAppConfigContentClient(connectionDetails);
	}

}
