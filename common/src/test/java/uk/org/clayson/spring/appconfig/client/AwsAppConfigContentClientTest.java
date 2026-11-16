package uk.org.clayson.spring.appconfig.client;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;
import software.amazon.awssdk.services.appconfigdata.model.GetLatestConfigurationRequest;
import software.amazon.awssdk.services.appconfigdata.model.GetLatestConfigurationResponse;
import software.amazon.awssdk.services.appconfigdata.model.StartConfigurationSessionRequest;
import software.amazon.awssdk.services.appconfigdata.model.StartConfigurationSessionResponse;

import uk.org.clayson.spring.appconfig.model.AppConfigConnectionDetails;
import uk.org.clayson.spring.appconfig.model.AppConfigContent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AwsAppConfigContentClientTest {

	private final AppConfigDataClient client = mock(AppConfigDataClient.class);

	private final AppConfigConnectionDetails connectionDetails = new AppConfigConnectionDetails("orders", "prod",
			"orders-api", "eu-west-1", null, null, Duration.ofSeconds(20), null, true);

	@Test
	void startsSessionAndReadsConfiguration() {
		when(this.client.startConfigurationSession(any(StartConfigurationSessionRequest.class)))
			.thenReturn(StartConfigurationSessionResponse.builder().initialConfigurationToken("initial-token").build());
		when(this.client.getLatestConfiguration(any(GetLatestConfigurationRequest.class)))
			.thenReturn(GetLatestConfigurationResponse.builder()
				.configuration(SdkBytes.fromUtf8String("greeting.message=Hello"))
				.contentType("text/plain")
				.versionLabel("v42")
				.nextPollConfigurationToken("next-token")
				.build());

		AppConfigContent content;
		try (AwsAppConfigContentClient client = new AwsAppConfigContentClient(this.client, this.connectionDetails)) {
			content = client.fetch();
		}

		assertThat(content.content()).isEqualTo("greeting.message=Hello");
		assertThat(content.contentType()).isEqualTo("text/plain");
		assertThat(content.versionLabel()).isEqualTo("v42");
		ArgumentCaptor<StartConfigurationSessionRequest> startCaptor = ArgumentCaptor
			.forClass(StartConfigurationSessionRequest.class);
		verify(this.client).startConfigurationSession(startCaptor.capture());
		assertThat(startCaptor.getValue().applicationIdentifier()).isEqualTo("orders");
		assertThat(startCaptor.getValue().environmentIdentifier()).isEqualTo("prod");
		assertThat(startCaptor.getValue().configurationProfileIdentifier()).isEqualTo("orders-api");
		assertThat(startCaptor.getValue().requiredMinimumPollIntervalInSeconds()).isEqualTo(20);
		ArgumentCaptor<GetLatestConfigurationRequest> latestCaptor = ArgumentCaptor
			.forClass(GetLatestConfigurationRequest.class);
		verify(this.client).getLatestConfiguration(latestCaptor.capture());
		assertThat(latestCaptor.getValue().configurationToken()).isEqualTo("initial-token");
	}

	@Test
	void reusesSessionAndRotatesPollToken() {
		when(this.client.startConfigurationSession(any(StartConfigurationSessionRequest.class)))
			.thenReturn(StartConfigurationSessionResponse.builder().initialConfigurationToken("token-1").build());
		when(this.client.getLatestConfiguration(any(GetLatestConfigurationRequest.class)))
			.thenReturn(GetLatestConfigurationResponse.builder()
				.configuration(SdkBytes.fromUtf8String("first"))
				.nextPollConfigurationToken("token-2")
				.build())
			.thenReturn(GetLatestConfigurationResponse.builder()
				.configuration(SdkBytes.fromUtf8String(""))
				.nextPollConfigurationToken("token-3")
				.build());

		try (AwsAppConfigContentClient client = new AwsAppConfigContentClient(this.client, this.connectionDetails)) {
			assertThat(client.fetch().content()).isEqualTo("first");
			AppConfigContent unchanged = client.fetch();
			assertThat(unchanged.isEmpty()).isTrue();
		}

		ArgumentCaptor<GetLatestConfigurationRequest> captor = ArgumentCaptor
			.forClass(GetLatestConfigurationRequest.class);
		verify(this.client, times(2)).getLatestConfiguration(captor.capture());
		assertThat(captor.getAllValues()).extracting(GetLatestConfigurationRequest::configurationToken)
			.containsExactly("token-1", "token-2");
	}

}
