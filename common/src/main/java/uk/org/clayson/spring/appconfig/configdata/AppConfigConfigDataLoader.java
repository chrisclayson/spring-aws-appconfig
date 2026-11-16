package uk.org.clayson.spring.appconfig.configdata;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;

import org.springframework.boot.context.config.ConfigData;
import org.springframework.boot.context.config.ConfigDataLoader;
import org.springframework.boot.context.config.ConfigDataLoaderContext;
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.util.Assert;

import uk.org.clayson.spring.appconfig.client.AppConfigContentClient;
import uk.org.clayson.spring.appconfig.exception.AppConfigException;
import uk.org.clayson.spring.appconfig.exception.AppConfigNotFoundException;
import uk.org.clayson.spring.appconfig.factory.AppConfigClientFactory;
import uk.org.clayson.spring.appconfig.factory.AwsAppConfigClientFactory;
import uk.org.clayson.spring.appconfig.model.AppConfigConnectionDetails;
import uk.org.clayson.spring.appconfig.model.AppConfigContent;
import uk.org.clayson.spring.appconfig.model.AppConfigContentType;
import uk.org.clayson.spring.appconfig.property.AppConfigPropertySource;
import uk.org.clayson.spring.appconfig.property.AppConfigPropertySourceLoader;

/**
 * Loads configuration data for a resolved {@code aws-appconfig:} location.
 */
public final class AppConfigConfigDataLoader implements ConfigDataLoader<AppConfigConfigDataResource> {

	private final Log log;

	private final AppConfigPropertySourceLoader propertySourceLoader = new AppConfigPropertySourceLoader();

	public AppConfigConfigDataLoader(DeferredLogFactory logFactory) {
		Assert.notNull(logFactory, "'logFactory' must not be null");
		this.log = logFactory.getLog(AppConfigConfigDataLoader.class);
	}

	@Override
	public ConfigData load(ConfigDataLoaderContext context, AppConfigConfigDataResource resource)
			throws IOException, ConfigDataResourceNotFoundException {
		AppConfigConnectionDetails connectionDetails = resource.getConnectionDetails();
		String description = connectionDetails.description();
		String propertySourceName = "aws-appconfig:" + description;
		try {
			AppConfigClientFactory clientFactory = resolveClientFactory(context);
			AppConfigContent content = AppConfigRefreshContext.get(connectionDetails);
			if (content == null) {
				content = fetch(clientFactory, connectionDetails);
			}
			AppConfigContentType contentType = (connectionDetails.contentType() != null) ? connectionDetails.contentType()
					: AppConfigContentType.detect(content.contentType(), AppConfigContentType.YAML);
			Map<String, Object> properties = this.propertySourceLoader.load(propertySourceName, content, contentType,
					connectionDetails.prefix());
			AppConfigPropertySource propertySource = new AppConfigPropertySource(propertySourceName,
					connectionDetails, clientFactory, properties, content.versionLabel());
			if (content.isEmpty()) {
				this.log.info("AWS AppConfig returned no configuration for " + description
						+ " (an empty property source was registered so that later changes can be refreshed)");
			}
			else {
				this.log.info("Loaded " + properties.size() + " propert(ies) from AWS AppConfig [" + description + "]"
						+ ((content.versionLabel() != null) ? " version " + content.versionLabel() : "") + " as "
						+ contentType);
			}
			return new ConfigData(List.of(propertySource));
		}
		catch (AppConfigNotFoundException ex) {
			throw new ConfigDataResourceNotFoundException(resource, ex);
		}
		catch (AppConfigException ex) {
			if (!connectionDetails.failFast() || resource.isOptionalResource()) {
				this.log.warn("Ignoring AWS AppConfig configuration [" + description + "]: " + ex.getMessage());
				return ConfigData.EMPTY;
			}
			throw ex;
		}
	}

	private AppConfigClientFactory resolveClientFactory(ConfigDataLoaderContext context) {
		// getBootstrapContext() is intentionally called without importing its return type:
		// ConfigurableBootstrapContext lives in org.springframework.boot.bootstrap on Boot
		// 4 and in org.springframework.boot on Boot 3. Compiling this source per module
		// produces the matching descriptor.
		AppConfigClientFactory factory = context.getBootstrapContext().getOrElse(AppConfigClientFactory.class, null);
		return (factory != null) ? factory : new AwsAppConfigClientFactory();
	}

	private AppConfigContent fetch(AppConfigClientFactory clientFactory,
			AppConfigConnectionDetails connectionDetails) {
		try (AppConfigContentClient client = clientFactory.create(connectionDetails)) {
			return client.fetch();
		}
	}

}
