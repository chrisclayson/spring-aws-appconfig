package uk.org.clayson.spring.appconfig.refresh;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

/**
 * Configuration for periodic AWS AppConfig refresh.
 */
@ConfigurationProperties(prefix = "appconfig.refresh")
public class AppConfigRefreshProperties {

	/**
	 * Whether to poll AWS AppConfig and refresh the application context when the
	 * configuration changes. Only used when Spring Cloud Context is on the classpath.
	 */
	private boolean enabled = true;

	/**
	 * Time between AppConfig polls. Should be at least
	 * {@code appconfig.minimum-poll-interval} (15 seconds by default).
	 */
	private Duration interval = Duration.ofSeconds(30);

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Duration getInterval() {
		return this.interval;
	}

	public void setInterval(Duration interval) {
		Assert.notNull(interval, "'interval' must not be null");
		Assert.isTrue(!interval.isNegative() && !interval.isZero(), "'interval' must be positive");
		this.interval = interval;
	}

}
