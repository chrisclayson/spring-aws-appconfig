package uk.org.clayson.spring.appconfig.configdata;

import java.util.List;

import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataLocationResolver;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.Ordered;
import org.springframework.util.Assert;

import uk.org.clayson.spring.appconfig.model.AppConfigConnectionDetails;

/**
 * Resolves {@code aws-appconfig:} config data locations, making them usable with
 * {@code spring.config.import}.
 * <p>
 * The location syntax is
 * {@code aws-appconfig:<application>[/<environment>[/<configuration-profile>]]}. Missing
 * segments fall back to the {@code appconfig.*} bootstrap properties.
 */
public final class AppConfigConfigDataLocationResolver
		implements ConfigDataLocationResolver<AppConfigConfigDataResource>, Ordered {

	/** The location prefix supported by this resolver. */
	public static final String PREFIX = "aws-appconfig:";

	private static final String PROPERTY_PREFIX = "appconfig";

	private final Binder binder;

	public AppConfigConfigDataLocationResolver(Binder binder) {
		Assert.notNull(binder, "'binder' must not be null");
		this.binder = binder;
	}

	@Override
	public boolean isResolvable(ConfigDataLocationResolverContext context, ConfigDataLocation location) {
		return location.hasPrefix(PREFIX);
	}

	@Override
	public List<AppConfigConfigDataResource> resolve(ConfigDataLocationResolverContext context,
			ConfigDataLocation location) {
		AppConfigBootstrapProperties properties = this.binder
			.bind(PROPERTY_PREFIX, Bindable.of(AppConfigBootstrapProperties.class))
			.orElseGet(AppConfigBootstrapProperties::new);
		AppConfigConnectionDetails connectionDetails = properties
			.toConnectionDetails(location.getNonPrefixedValue(PREFIX));
		return List.of(new AppConfigConfigDataResource(connectionDetails, location.isOptional()));
	}

	@Override
	public int getOrder() {
		// Standard config data locations are re-ordered to the end by Spring Boot, but an
		// explicit high order keeps this resolver ahead of any other custom resolver.
		return Ordered.HIGHEST_PRECEDENCE;
	}

}
