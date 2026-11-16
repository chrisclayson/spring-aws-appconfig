package uk.org.clayson.spring.appconfig.autoconfigure;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.context.refresh.ContextRefresher;

import uk.org.clayson.spring.appconfig.refresh.AppConfigRefreshProperties;
import uk.org.clayson.spring.appconfig.refresh.AppConfigRefresher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AppConfigRefreshAutoConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(AppConfigRefreshAutoConfiguration.class))
		.withBean(ContextRefresher.class, () -> mock(ContextRefresher.class));

	@Test
	void createsRefresherByDefault() {
		this.contextRunner.withPropertyValues("appconfig.refresh.interval=45s").run((context) -> {
			assertThat(context).hasSingleBean(AppConfigRefresher.class);
			assertThat(context.getBean(AppConfigRefreshProperties.class).getInterval())
				.isEqualTo(Duration.ofSeconds(45));
		});
	}

	@Test
	void canBeDisabled() {
		this.contextRunner.withPropertyValues("appconfig.refresh.enabled=false")
			.run((context) -> assertThat(context).doesNotHaveBean(AppConfigRefresher.class));
	}

	@Test
	void doesNothingWithoutContextRefresherBean() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(AppConfigRefreshAutoConfiguration.class))
			.run((context) -> assertThat(context).doesNotHaveBean(AppConfigRefresher.class));
	}

}
