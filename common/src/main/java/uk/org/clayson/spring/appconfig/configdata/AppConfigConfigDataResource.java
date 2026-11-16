package uk.org.clayson.spring.appconfig.configdata;

import java.util.Objects;

import org.springframework.boot.context.config.ConfigDataResource;

import uk.org.clayson.spring.appconfig.model.AppConfigConnectionDetails;

/**
 * A resolved {@code aws-appconfig:} location.
 */
public final class AppConfigConfigDataResource extends ConfigDataResource {

	private final AppConfigConnectionDetails connectionDetails;

	private final boolean optional;

	public AppConfigConfigDataResource(AppConfigConnectionDetails connectionDetails, boolean optional) {
		super(optional);
		this.connectionDetails = Objects.requireNonNull(connectionDetails, "'connectionDetails' must not be null");
		this.optional = optional;
	}

	public AppConfigConnectionDetails getConnectionDetails() {
		return this.connectionDetails;
	}

	public boolean isOptionalResource() {
		return this.optional;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof AppConfigConfigDataResource that)) {
			return false;
		}
		return this.optional == that.optional && this.connectionDetails.equals(that.connectionDetails);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.connectionDetails, this.optional);
	}

	@Override
	public String toString() {
		return "AWS AppConfig [" + this.connectionDetails.description() + "]"
				+ (this.optional ? " (optional)" : "");
	}

}
