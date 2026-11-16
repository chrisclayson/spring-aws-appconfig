package uk.org.clayson.spring.appconfig.model;

/**
 * A configuration payload returned by AWS AppConfig.
 *
 * @param content the UTF-8 decoded configuration body (never {@code null})
 * @param contentType the {@code Content-Type} reported by AWS AppConfig (may be
 * {@code null})
 * @param versionLabel the AppConfig version label (may be {@code null})
 */
public record AppConfigContent(String content, String contentType, String versionLabel) {

	public AppConfigContent {
		content = (content != null) ? content : "";
	}

	public boolean isEmpty() {
		return this.content.isEmpty();
	}

}
