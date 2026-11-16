package uk.org.clayson.spring.appconfig.refresh;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.core.env.StandardEnvironment;

import uk.org.clayson.spring.appconfig.client.AppConfigContentClient;
import uk.org.clayson.spring.appconfig.exception.AppConfigException;
import uk.org.clayson.spring.appconfig.model.AppConfigConnectionDetails;
import uk.org.clayson.spring.appconfig.model.AppConfigContent;
import uk.org.clayson.spring.appconfig.model.AppConfigContentType;
import uk.org.clayson.spring.appconfig.property.AppConfigPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AppConfigRefresherTest {

	private static final AppConfigContent EMPTY = new AppConfigContent("", null, null);

	private final StandardEnvironment environment = new StandardEnvironment();

	private final ContextRefresher contextRefresher = mock(ContextRefresher.class);

	private final AppConfigRefreshProperties properties = new AppConfigRefreshProperties();

	private AppConfigRefresher refresher;

	@AfterEach
	void stopRefresher() {
		if (this.refresher != null) {
			this.refresher.stop();
		}
	}

	@Test
	void updatesPropertySourceAndRefreshesContext() {
		AppConfigPropertySource source = addSource("aws-appconfig:app/env/profile", Map.of("greeting.message", "old"),
				"1", () -> new AppConfigContent("greeting.message: updated", "application/x-yaml", "2"));

		startRefresher();
		this.refresher.refreshOnce();

		assertThat(source.getProperty("greeting.message")).isEqualTo("updated");
		assertThat(source.getVersionLabel()).isEqualTo("2");
		verify(this.contextRefresher).refresh();
	}

	@Test
	void replacesAllExistingValues() {
		AppConfigPropertySource source = addSource("aws-appconfig:app/env/profile",
				Map.of("greeting.message", "old", "removed.property", "gone"), "1",
				() -> new AppConfigContent("greeting.message: updated", "application/x-yaml", "2"));

		startRefresher();
		this.refresher.refreshOnce();

		assertThat(source.getProperty("greeting.message")).isEqualTo("updated");
		assertThat(source.getProperty("removed.property")).isNull();
	}

	@Test
	void emptyPayloadMeansUnchanged() {
		AppConfigPropertySource source = addSource("aws-appconfig:app/env/profile", Map.of("greeting.message", "old"),
				"1", () -> EMPTY);

		startRefresher();
		this.refresher.refreshOnce();

		assertThat(source.getProperty("greeting.message")).isEqualTo("old");
		verify(this.contextRefresher, never()).refresh();
	}

	@Test
	void sameVersionDoesNotTriggerRefresh() {
		AppConfigPropertySource source = addSource("aws-appconfig:app/env/profile", Map.of("greeting.message", "old"),
				"1", () -> new AppConfigContent("greeting.message: updated", "application/x-yaml", "1"));

		startRefresher();
		this.refresher.refreshOnce();

		assertThat(source.getProperty("greeting.message")).isEqualTo("old");
		verify(this.contextRefresher, never()).refresh();
	}

	@Test
	void failedPollIsIgnoredAndPollingContinues() {
		AtomicBoolean first = new AtomicBoolean(true);
		AppConfigPropertySource source = addSource("aws-appconfig:app/env/profile", Map.of("greeting.message", "old"),
				"1", () -> {
					if (first.getAndSet(false)) {
						throw new AppConfigException("boom");
					}
					return new AppConfigContent("greeting.message: updated", "application/x-yaml", "2");
				});

		startRefresher();
		this.refresher.refreshOnce();
		assertThat(source.getProperty("greeting.message")).isEqualTo("old");

		this.refresher.refreshOnce();
		assertThat(source.getProperty("greeting.message")).isEqualTo("updated");
		verify(this.contextRefresher).refresh();
	}

	@Test
	void oneRefreshForMultipleChangedSources() {
		addSource("aws-appconfig:app/env/first", Map.of("first", "old"), "1",
				() -> new AppConfigContent("first: new", "application/x-yaml", "2"));
		addSource("aws-appconfig:app/env/second", Map.of("second", "old"), "1",
				() -> new AppConfigContent("second: new", "application/x-yaml", "2"));

		startRefresher();
		this.refresher.refreshOnce();

		verify(this.contextRefresher, times(1)).refresh();
	}

	@Test
	void ignoresClientsThatFailToBeCreated() {
		AppConfigConnectionDetails details = details();
		AppConfigPropertySource source = new AppConfigPropertySource("aws-appconfig:app/env/profile", details,
				(connectionDetails) -> {
					throw new AppConfigException("cannot create client");
				}, Map.of("greeting.message", "old"), "1");
		this.environment.getPropertySources().addLast(source);

		startRefresher();

		assertThat(source.getProperty("greeting.message")).isEqualTo("old");
		verify(this.contextRefresher, never()).refresh();
	}

	private void startRefresher() {
		this.properties.setInterval(Duration.ofHours(1));
		this.refresher = new AppConfigRefresher(this.environment, this.contextRefresher, this.properties);
		this.refresher.start();
	}

	private AppConfigPropertySource addSource(String name, Map<String, Object> initialProperties, String version,
			AppConfigContentClient client) {
		AppConfigPropertySource source = new AppConfigPropertySource(name, details(), (connectionDetails) -> client,
				initialProperties, version);
		this.environment.getPropertySources().addLast(source);
		return source;
	}

	private static AppConfigConnectionDetails details() {
		return new AppConfigConnectionDetails("app", "env", "profile", null, null, AppConfigContentType.YAML,
				Duration.ofSeconds(15), null, true);
	}

}
