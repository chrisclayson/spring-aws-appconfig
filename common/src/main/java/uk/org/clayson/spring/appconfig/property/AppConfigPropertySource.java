package uk.org.clayson.spring.appconfig.property;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.core.env.EnumerablePropertySource;

import uk.org.clayson.spring.appconfig.factory.AppConfigClientFactory;
import uk.org.clayson.spring.appconfig.model.AppConfigConnectionDetails;

/**
 * A mutable Spring {@link org.springframework.core.env.PropertySource} backed by an AWS
 * AppConfig hosted configuration.
 * <p>
 * The source is created while config data is loaded and updated in place by
 * {@code AppConfigRefresher}, which allows the poller to change values without replacing
 * the property source in the {@code Environment}. It also carries the connection details
 * and the client factory so that a refresh can reuse the same AWS configuration that was
 * used at startup.
 */
public final class AppConfigPropertySource extends EnumerablePropertySource<Map<String, Object>> {

	private final AppConfigConnectionDetails connectionDetails;

	private final AppConfigClientFactory clientFactory;

	private volatile Map<String, Object> properties;

	private volatile String versionLabel;

	public AppConfigPropertySource(String name, AppConfigConnectionDetails connectionDetails,
			AppConfigClientFactory clientFactory, Map<String, Object> properties, String versionLabel) {
		super(name, Collections.emptyMap());
		this.connectionDetails = Objects.requireNonNull(connectionDetails, "'connectionDetails' must not be null");
		this.clientFactory = Objects.requireNonNull(clientFactory, "'clientFactory' must not be null");
		this.properties = immutableCopy(properties);
		this.versionLabel = versionLabel;
	}

	/**
	 * Replace the current values with a freshly loaded configuration.
	 * @param properties the new properties
	 * @param versionLabel the AppConfig version label, if available
	 */
	public void replace(Map<String, Object> properties, String versionLabel) {
		this.properties = immutableCopy(properties);
		this.versionLabel = versionLabel;
	}

	public AppConfigConnectionDetails getConnectionDetails() {
		return this.connectionDetails;
	}

	public String getVersionLabel() {
		return this.versionLabel;
	}

	public AppConfigClientFactory getClientFactory() {
		return this.clientFactory;
	}

	@Override
	public Object getProperty(String name) {
		return this.properties.get(name);
	}

	@Override
	public String[] getPropertyNames() {
		return this.properties.keySet().toArray(String[]::new);
	}

	@Override
	public boolean containsProperty(String name) {
		return this.properties.containsKey(name);
	}

	private static Map<String, Object> immutableCopy(Map<String, Object> properties) {
		return Collections.unmodifiableMap(new LinkedHashMap<>(properties));
	}

}
