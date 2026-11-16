package uk.org.clayson.spring.appconfig.property;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import uk.org.clayson.spring.appconfig.model.AppConfigContent;
import uk.org.clayson.spring.appconfig.model.AppConfigContentType;

/**
 * Parses an AppConfig payload into a flat map of property names to values, using the same
 * parsers that Spring Boot uses for local configuration files.
 */
public final class AppConfigPropertySourceLoader {

	private static final PropertySourceLoader YAML_PROPERTY_SOURCE_LOADER = new YamlPropertySourceLoader();

	private static final PropertySourceLoader PROPERTIES_PROPERTY_SOURCE_LOADER = new PropertiesPropertySourceLoader();

	public Map<String, Object> load(String name, AppConfigContent content, AppConfigContentType contentType,
			String prefix) throws IOException {
		if (content.isEmpty()) {
			return Map.of();
		}
		Resource resource = new NamedByteArrayResource(content.content().getBytes(StandardCharsets.UTF_8),
				"appconfig." + contentType.extension());
		PropertySourceLoader propertySourceLoader = (contentType == AppConfigContentType.PROPERTIES)
				? PROPERTIES_PROPERTY_SOURCE_LOADER : YAML_PROPERTY_SOURCE_LOADER;
		List<PropertySource<?>> loaded = propertySourceLoader.load(name, resource);
		Map<String, Object> properties = new LinkedHashMap<>();
		String propertyPrefix = StringUtils.hasText(prefix) ? prefix + "." : "";
		for (PropertySource<?> source : loaded) {
			if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
				throw new IllegalStateException(
						"Cannot read properties from non-enumerable property source " + source.getName());
			}
			for (String propertyName : enumerable.getPropertyNames()) {
				properties.put(propertyPrefix + propertyName, unwrap(enumerable.getProperty(propertyName)));
			}
		}
		return properties;
	}

	private static Object unwrap(Object value) {
		return (value instanceof OriginTrackedValue originTrackedValue) ? originTrackedValue.getValue() : value;
	}

	private static final class NamedByteArrayResource extends ByteArrayResource {

		private final String filename;

		private NamedByteArrayResource(byte[] byteArray, String filename) {
			super(byteArray);
			this.filename = filename;
		}

		@Override
		public String getFilename() {
			return this.filename;
		}

	}

}
