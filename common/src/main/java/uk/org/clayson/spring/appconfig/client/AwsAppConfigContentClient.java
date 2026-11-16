package uk.org.clayson.spring.appconfig.client;

import java.net.URI;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClientBuilder;
import software.amazon.awssdk.services.appconfigdata.model.GetLatestConfigurationRequest;
import software.amazon.awssdk.services.appconfigdata.model.GetLatestConfigurationResponse;
import software.amazon.awssdk.services.appconfigdata.model.ResourceNotFoundException;
import software.amazon.awssdk.services.appconfigdata.model.StartConfigurationSessionRequest;
import software.amazon.awssdk.services.appconfigdata.model.StartConfigurationSessionResponse;
import software.amazon.awssdk.utils.StringUtils;

import uk.org.clayson.spring.appconfig.exception.AppConfigException;
import uk.org.clayson.spring.appconfig.exception.AppConfigNotFoundException;
import uk.org.clayson.spring.appconfig.model.AppConfigConnectionDetails;
import uk.org.clayson.spring.appconfig.model.AppConfigContent;

/**
 * Reads a hosted configuration through the AWS AppConfig data plane.
 * <p>
 * The first call starts a configuration session and uses its initial token. Subsequent
 * calls reuse and rotate the poll token, which is what a scheduled refresher needs. A
 * fetch returns an empty payload when the configuration has not changed since the
 * previous fetch.
 */
public final class AwsAppConfigContentClient implements AppConfigContentClient {

	private final AppConfigDataClient client;

	private final AppConfigConnectionDetails connectionDetails;

	private String configurationToken;

	public AwsAppConfigContentClient(AppConfigConnectionDetails connectionDetails) {
		this(createClient(connectionDetails), connectionDetails);
	}

	private static AppConfigDataClient createClient(AppConfigConnectionDetails connectionDetails) {
		try {
			AppConfigDataClientBuilder builder = AppConfigDataClient.builder();
			if (connectionDetails.region() != null) {
				builder.region(Region.of(connectionDetails.region()));
			}
			URI endpoint = connectionDetails.endpoint();
			if (endpoint != null) {
				builder.endpointOverride(endpoint);
			}
			return builder.build();
		}
		catch (RuntimeException ex) {
			throw new AppConfigException("Unable to create an AWS AppConfig client for "
					+ connectionDetails.description()
					+ ". Configure a region with 'appconfig.region' or the AWS_REGION environment variable.", ex);
		}
	}

	public AwsAppConfigContentClient(AppConfigDataClient client, AppConfigConnectionDetails connectionDetails) {
		this.client = client;
		this.connectionDetails = connectionDetails;
	}

	@Override
	public synchronized AppConfigContent fetch() {
		try {
			if (this.configurationToken == null) {
				this.configurationToken = startSession();
			}
			GetLatestConfigurationResponse response = this.client.getLatestConfiguration(GetLatestConfigurationRequest
				.builder()
				.configurationToken(this.configurationToken)
				.build());
			if (StringUtils.isNotBlank(response.nextPollConfigurationToken())) {
				this.configurationToken = response.nextPollConfigurationToken();
			}
			return new AppConfigContent(
					(response.configuration() != null) ? response.configuration().asUtf8String() : "",
					response.contentType(), response.versionLabel());
		}
		catch (ResourceNotFoundException ex) {
			throw new AppConfigNotFoundException(
					"AWS AppConfig configuration not found: " + this.connectionDetails.description(), ex);
		}
		catch (SdkException ex) {
			throw new AppConfigException(
					"Unable to read AWS AppConfig configuration " + this.connectionDetails.description(), ex);
		}
	}

	private String startSession() {
		StartConfigurationSessionResponse response = this.client.startConfigurationSession(
				StartConfigurationSessionRequest.builder()
					.applicationIdentifier(this.connectionDetails.application())
					.environmentIdentifier(this.connectionDetails.environment())
					.configurationProfileIdentifier(this.connectionDetails.configurationProfile())
					.requiredMinimumPollIntervalInSeconds(
							(int) this.connectionDetails.minimumPollInterval().toSeconds())
					.build());
		return response.initialConfigurationToken();
	}

	@Override
	public void close() {
		this.client.close();
	}

}
