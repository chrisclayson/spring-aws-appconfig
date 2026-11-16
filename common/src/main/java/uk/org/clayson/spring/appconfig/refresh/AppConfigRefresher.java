package uk.org.clayson.spring.appconfig.refresh;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.util.Assert;

import uk.org.clayson.spring.appconfig.client.AppConfigContentClient;
import uk.org.clayson.spring.appconfig.configdata.AppConfigRefreshContext;
import uk.org.clayson.spring.appconfig.exception.AppConfigNotFoundException;
import uk.org.clayson.spring.appconfig.model.AppConfigConnectionDetails;
import uk.org.clayson.spring.appconfig.model.AppConfigContent;
import uk.org.clayson.spring.appconfig.model.AppConfigContentType;
import uk.org.clayson.spring.appconfig.property.AppConfigPropertySource;
import uk.org.clayson.spring.appconfig.property.AppConfigPropertySourceLoader;

/**
 * Polls AWS AppConfig and refreshes the Spring application context when a hosted
 * configuration changes.
 * <p>
 * Refreshing is only enabled when Spring Cloud Context (and therefore a
 * {@link ContextRefresher} bean) is present. The poller updates the mutable
 * {@link AppConfigPropertySource} in place and then calls {@link ContextRefresher#refresh()}
 * so that {@code @ConfigurationProperties} beans and {@code @RefreshScope} beans see the
 * new values.
 * <p>
 * A long-lived data-plane client per configuration source is used, so an empty payload
 * from AppConfig means "unchanged".
 */
public final class AppConfigRefresher implements SmartLifecycle {

	private static final Log log = LogFactory.getLog(AppConfigRefresher.class);

	private final ConfigurableEnvironment environment;

	private final ContextRefresher contextRefresher;

	private final AppConfigRefreshProperties properties;

	private final AppConfigPropertySourceLoader propertySourceLoader = new AppConfigPropertySourceLoader();

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor((runnable) -> {
		Thread thread = new Thread(runnable, "appconfig-refresh");
		thread.setDaemon(true);
		return thread;
	});

	private final Map<String, RefreshTarget> targets = new ConcurrentHashMap<>();

	private volatile boolean running;

	public AppConfigRefresher(ConfigurableEnvironment environment, ContextRefresher contextRefresher,
			AppConfigRefreshProperties properties) {
		Assert.notNull(environment, "'environment' must not be null");
		Assert.notNull(contextRefresher, "'contextRefresher' must not be null");
		Assert.notNull(properties, "'properties' must not be null");
		this.environment = environment;
		this.contextRefresher = contextRefresher;
		this.properties = properties;
	}

	@Override
	public synchronized void start() {
		if (this.running) {
			return;
		}
		refreshTargets();
		Duration interval = this.properties.getInterval();
		this.executor.scheduleWithFixedDelay(this::refreshOnce, interval.toMillis(), interval.toMillis(),
				TimeUnit.MILLISECONDS);
		this.running = true;
		log.info("AWS AppConfig refresh enabled (interval " + interval + ", " + this.targets.size()
				+ " configuration source(s))");
	}

	@Override
	public synchronized void stop() {
		if (!this.running) {
			return;
		}
		this.running = false;
		this.executor.shutdownNow();
		this.targets.values().forEach(RefreshTarget::close);
		this.targets.clear();
		log.info("AWS AppConfig refresh stopped");
	}

	@Override
	public boolean isRunning() {
		return this.running;
	}

	/**
	 * Perform one poll cycle. Package-private so that it can be tested without waiting
	 * for the scheduler.
	 */
	void refreshOnce() {
		if (!this.running) {
			return;
		}
		Map<AppConfigConnectionDetails, AppConfigContent> changedContent = new HashMap<>();
		refreshTargets();
		for (RefreshTarget target : this.targets.values()) {
			try {
				AppConfigContent content = target.refresh();
				if (content != null) {
					changedContent.put(target.getConnectionDetails(), content);
				}
			}
			catch (AppConfigNotFoundException ex) {
				log.debug("AWS AppConfig configuration no longer exists: " + ex.getMessage());
			}
			catch (IOException | RuntimeException ex) {
				log.warn("Unable to refresh AWS AppConfig configuration", ex);
			}
		}
		if (!changedContent.isEmpty()) {
			try {
				AppConfigRefreshContext.run(changedContent, this.contextRefresher::refresh);
				log.info("Application context refreshed from AWS AppConfig (" + changedContent.size()
						+ " configuration source(s))");
			}
			catch (RuntimeException ex) {
				log.warn("Unable to refresh the application context from AWS AppConfig", ex);
			}
		}
	}

	private void refreshTargets() {
		Set<String> currentNames = new HashSet<>();
		for (PropertySource<?> propertySource : this.environment.getPropertySources()) {
			if (propertySource instanceof AppConfigPropertySource appConfigSource) {
				currentNames.add(appConfigSource.getName());
				this.targets.compute(appConfigSource.getName(), (name, target) -> {
					if (target == null) {
						return new RefreshTarget(appConfigSource);
					}
					target.setSource(appConfigSource);
					return target;
				});
			}
		}
		this.targets.entrySet().removeIf((entry) -> {
			if (currentNames.contains(entry.getKey())) {
				return false;
			}
			entry.getValue().close();
			return true;
		});
	}

	private final class RefreshTarget implements AutoCloseable {

		private volatile AppConfigPropertySource source;

		private AppConfigContentClient client;

		private RefreshTarget(AppConfigPropertySource source) {
			this.source = source;
		}

		private void setSource(AppConfigPropertySource source) {
			if (!source.getConnectionDetails().equals(this.source.getConnectionDetails())) {
				closeClient();
			}
			this.source = source;
		}

		private AppConfigContentClient createClient(AppConfigPropertySource source) {
			return source.getClientFactory().create(source.getConnectionDetails());
		}

		private AppConfigContent refresh() throws IOException {
			AppConfigContent content = getClient().fetch();
			if (content.isEmpty()) {
				return null;
			}
			AppConfigPropertySource currentSource = this.source;
			if (content.versionLabel() != null && content.versionLabel().equals(currentSource.getVersionLabel())) {
				return null;
			}
			AppConfigConnectionDetails connectionDetails = currentSource.getConnectionDetails();
			AppConfigContentType contentType = (connectionDetails.contentType() != null) ? connectionDetails.contentType()
					: AppConfigContentType.detect(content.contentType(), AppConfigContentType.YAML);
			Map<String, Object> properties = AppConfigRefresher.this.propertySourceLoader
				.load(currentSource.getName(), content, contentType, connectionDetails.prefix());
			currentSource.replace(properties, content.versionLabel());
			log.info("Updated AWS AppConfig configuration [" + connectionDetails.description() + "]"
					+ ((content.versionLabel() != null) ? " version " + content.versionLabel() : ""));
			return content;
		}

		private synchronized AppConfigContentClient getClient() {
			if (this.client == null) {
				this.client = createClient(this.source);
			}
			return this.client;
		}

		private AppConfigConnectionDetails getConnectionDetails() {
			return this.source.getConnectionDetails();
		}

		private synchronized void closeClient() {
			if (this.client != null) {
				this.client.close();
				this.client = null;
			}
		}

		@Override
		public void close() {
			closeClient();
		}

	}

}
