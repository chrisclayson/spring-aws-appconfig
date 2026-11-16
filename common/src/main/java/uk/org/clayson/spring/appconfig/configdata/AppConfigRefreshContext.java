package uk.org.clayson.spring.appconfig.configdata;

import java.util.HashMap;
import java.util.Map;

import uk.org.clayson.spring.appconfig.model.AppConfigConnectionDetails;
import uk.org.clayson.spring.appconfig.model.AppConfigContent;

/**
 * Shares the configuration content fetched by {@code AppConfigRefresher} with the config
 * data loading that happens inside {@code ContextRefresher.refresh()}.
 * <p>
 * Spring Cloud's {@code ConfigDataContextRefresher} re-runs the environment post
 * processors, which would otherwise fetch the configuration from AWS a second time.
 * Keeping the content in a thread local for the duration of the refresh avoids that
 * duplicate call and, more importantly, lets the reload reuse the same client
 * configuration (including a custom {@code AppConfigClientFactory}) that was used at
 * startup.
 */
public final class AppConfigRefreshContext {

	private static final ThreadLocal<Map<AppConfigConnectionDetails, AppConfigContent>> CONTENT = new ThreadLocal<>();

	private AppConfigRefreshContext() {
	}

	public static AppConfigContent get(AppConfigConnectionDetails connectionDetails) {
		Map<AppConfigConnectionDetails, AppConfigContent> content = CONTENT.get();
		return (content != null) ? content.get(connectionDetails) : null;
	}

	public static void run(Map<AppConfigConnectionDetails, AppConfigContent> content, Runnable action) {
		Map<AppConfigConnectionDetails, AppConfigContent> previous = CONTENT.get();
		CONTENT.set(new HashMap<>(content));
		try {
			action.run();
		}
		finally {
			if (previous != null) {
				CONTENT.set(previous);
			}
			else {
				CONTENT.remove();
			}
		}
	}

}
