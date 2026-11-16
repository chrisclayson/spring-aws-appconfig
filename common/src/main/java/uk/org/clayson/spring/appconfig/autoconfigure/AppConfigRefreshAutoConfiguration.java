package uk.org.clayson.spring.appconfig.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;

import uk.org.clayson.spring.appconfig.refresh.AppConfigRefreshProperties;
import uk.org.clayson.spring.appconfig.refresh.AppConfigRefresher;

/**
 * Auto-configuration that enables periodic AppConfig refresh when Spring Cloud Context
 * is on the classpath.
 * <p>
 * Refresh is enabled by default in that case and can be disabled with
 * {@code appconfig.refresh.enabled=false}. Without Spring Cloud Context this
 * auto-configuration backs off entirely and AppConfig is loaded once at startup.
 */
@AutoConfiguration(afterName = "org.springframework.cloud.autoconfigure.RefreshAutoConfiguration")
@ConditionalOnClass(ContextRefresher.class)
@ConditionalOnBean(ContextRefresher.class)
@ConditionalOnProperty(prefix = "appconfig.refresh", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AppConfigRefreshProperties.class)
public class AppConfigRefreshAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public AppConfigRefresher appConfigRefresher(ConfigurableEnvironment environment,
			ContextRefresher contextRefresher, AppConfigRefreshProperties properties) {
		return new AppConfigRefresher(environment, contextRefresher, properties);
	}

}
