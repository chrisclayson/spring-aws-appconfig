package uk.org.clayson.spring.appconfig.example;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.appconfig.AppConfigClient;
import software.amazon.awssdk.services.appconfig.model.DeploymentState;
import software.amazon.awssdk.services.appconfig.model.GrowthType;
import software.amazon.awssdk.services.appconfig.model.ReplicateTo;
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;

import uk.org.clayson.spring.appconfig.client.AwsAppConfigContentClient;
import uk.org.clayson.spring.appconfig.factory.AppConfigClientFactory;

import static org.awaitility.Awaitility.await;

/**
 * Provisions AWS AppConfig resources for the example application.
 * <p>
 * By default this starts AWS Emulator in Docker (through Testcontainers) and points
 * everything at it. Set {@code APPCONFIG_IT_MODE=real} to provision a temporary
 * application in the real AWS account instead; the normal AWS credential and region
 * providers are used in that mode.
 */
final class AppConfigTestEnvironment implements AutoCloseable {

	private static final Logger LOG = LoggerFactory.getLogger(AppConfigTestEnvironment.class);

	private static final String MODE = System.getenv().getOrDefault("APPCONFIG_IT_MODE", "emulator");

	private static final int EMULATOR_PORT = 9297;

	private static final String EMULATOR_HOME = "/home/emulator/.aws/emulator/" + EMULATOR_PORT;

	private final String mode;

	private final Region region;

	private final URI endpoint;

	private final AppConfigClient appConfig;

	private final AppConfigClientFactory clientFactory;

	private final GenericContainer<?> emulator;

	private final Path tempDirectory;

	private String applicationName;

	private String applicationId;

	private String environmentId;

	private String configurationProfileId;

	private String deploymentStrategyName;

	private String deploymentStrategyId;

	private Integer deploymentNumber;

	private AppConfigTestEnvironment(String mode, Region region, URI endpoint, AppConfigClient appConfig,
			AppConfigClientFactory clientFactory, GenericContainer<?> emulator, Path tempDirectory) {
		this.mode = mode;
		this.region = region;
		this.endpoint = endpoint;
		this.appConfig = appConfig;
		this.clientFactory = clientFactory;
		this.emulator = emulator;
		this.tempDirectory = tempDirectory;
	}

	static AppConfigTestEnvironment start() throws Exception {
		if ("real".equalsIgnoreCase(MODE)) {
			return startReal();
		}
		if (!"emulator".equalsIgnoreCase(MODE)) {
			throw new IllegalArgumentException("Unsupported APPCONFIG_IT_MODE '" + MODE + "'; use 'emulator' or 'real'");
		}
		return startEmulator();
	}

	private static AppConfigTestEnvironment startReal() {
		Region region = Region.of(firstNonBlank(System.getenv("APPCONFIG_IT_REGION"), System.getenv("AWS_REGION"),
				"us-east-1"));
		DefaultCredentialsProvider credentials = DefaultCredentialsProvider.create();
		try {
			credentials.resolveCredentials();
		}
		catch (RuntimeException ex) {
			throw new IllegalStateException(
					"APPCONFIG_IT_MODE=real requires AWS credentials (for example AWS_PROFILE or AWS_ACCESS_KEY_ID)",
					ex);
		}
		AppConfigClient appConfig = AppConfigClient.builder()
			.region(region)
			.credentialsProvider(credentials)
			.build();
		LOG.info("Running the AppConfig integration test against real AWS in {}", region);
		return new AppConfigTestEnvironment("real", region, null, appConfig, null, null, null);
	}

	private static AppConfigTestEnvironment startEmulator() throws Exception {
		String dockerfile = System.getProperty("awsEmulatorDockerfile");
		if (dockerfile == null) {
			throw new IllegalStateException("The 'awsEmulatorDockerfile' system property is not set");
		}
		Path tempDirectory = Files.createTempDirectory("appconfig-it-");
		GenericContainer<?> emulator = null;
		try {
			ImageFromDockerfile image = new ImageFromDockerfile("aws-emulator-appconfig-it", false)
				.withDockerfile(Path.of(dockerfile));
			emulator = new GenericContainer<>(image).withExposedPorts(EMULATOR_PORT)
				.withEnv("EMULATOR_TELEMETRY", "off")
				.withStartupTimeout(Duration.ofMinutes(10))
				.waitingFor(Wait.forListeningPort());
			LOG.info("Starting AWS Emulator (the first run builds the image)...");
			emulator.start();

			URI endpoint = URI.create("https://" + emulator.getHost() + ":" + emulator.getMappedPort(EMULATOR_PORT));
			GenericContainer<?> startedEmulator = emulator;

			Path truststore = tempDirectory.resolve("truststore.p12");
			await().atMost(Duration.ofMinutes(2))
				.pollInterval(Duration.ofSeconds(1))
				.until(() -> copyFromContainer(startedEmulator, EMULATOR_HOME + "/truststore.p12", truststore));
			SslMaterial ssl = SslMaterial.from(truststore);
			awaitHealthy(endpoint, ssl);

			Path credentialsFile = tempDirectory.resolve("emulator_credentials");
			await().atMost(Duration.ofSeconds(30))
				.pollInterval(Duration.ofSeconds(1))
				.until(() -> copyFromContainer(startedEmulator, EMULATOR_HOME + "/emulator_credentials",
						credentialsFile));
			AwsCredentials credentials = readCredentials(credentialsFile);
			AwsCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);

			AppConfigClient appConfig = AppConfigClient.builder()
				.endpointOverride(endpoint)
				.region(Region.US_EAST_1)
				.credentialsProvider(credentialsProvider)
				.httpClient(ssl.sdkHttpClient())
				.build();

			AppConfigClientFactory clientFactory = (connectionDetails) -> {
				AppConfigDataClient data = AppConfigDataClient.builder()
					.endpointOverride(endpoint)
					.region(Region.US_EAST_1)
					.credentialsProvider(credentialsProvider)
					.httpClient(ssl.sdkHttpClient())
					.build();
				return new AwsAppConfigContentClient(data, connectionDetails);
			};

			LOG.info("AWS Emulator is ready at {}", endpoint);
			return new AppConfigTestEnvironment("emulator", Region.US_EAST_1, endpoint, appConfig, clientFactory,
					emulator, tempDirectory);
		}
		catch (Exception ex) {
			if (emulator != null) {
				emulator.stop();
			}
			deleteRecursively(tempDirectory);
			throw ex;
		}
	}

	void publishConfiguration(String yaml) {
		this.applicationName = "appconfig-it-" + UUID.randomUUID();
		this.applicationId = this.appConfig.createApplication((request) -> request.name(this.applicationName)).id();
		this.environmentId = this.appConfig
			.createEnvironment((request) -> request.applicationId(this.applicationId).name("test"))
			.id();
		this.configurationProfileId = this.appConfig
			.createConfigurationProfile(
					(request) -> request.applicationId(this.applicationId).name("greeting").locationUri("hosted"))
			.id();
		this.deploymentStrategyName = "appconfig-it-" + UUID.randomUUID();
		this.deploymentStrategyId = this.appConfig.createDeploymentStrategy((request) -> request
			.name(this.deploymentStrategyName)
			.deploymentDurationInMinutes(0)
			.growthType(GrowthType.LINEAR)
			.growthFactor(100.0f)
			.finalBakeTimeInMinutes(0)
			.replicateTo(ReplicateTo.NONE)).id();
		Integer version = this.appConfig.createHostedConfigurationVersion((request) -> request
			.applicationId(this.applicationId)
			.configurationProfileId(this.configurationProfileId)
			.contentType("application/x-yaml")
			.content(SdkBytes.fromUtf8String(yaml))).versionNumber();
		this.deploymentNumber = this.appConfig.startDeployment((request) -> request
			.applicationId(this.applicationId)
			.environmentId(this.environmentId)
			.deploymentStrategyId(this.deploymentStrategyId)
			.configurationProfileId(this.configurationProfileId)
			.configurationVersion(String.valueOf(version))).deploymentNumber();
		awaitDeployment();
	}

	String location() {
		return this.applicationId + "/" + this.environmentId + "/" + this.configurationProfileId;
	}

	String mode() {
		return this.mode;
	}

	Region region() {
		return this.region;
	}

	URI endpoint() {
		return this.endpoint;
	}

	/**
	 * The factory to register in the Spring Boot bootstrap registry, or {@code null} to
	 * use the library's default AWS client (real AWS mode).
	 */
	AppConfigClientFactory clientFactory() {
		return this.clientFactory;
	}

	private void awaitDeployment() {
		Duration timeout = "real".equals(this.mode) ? Duration.ofMinutes(15) : Duration.ofMinutes(2);
		await().atMost(timeout).pollInterval(Duration.ofSeconds(2)).until(() -> {
			DeploymentState state = this.appConfig
				.getDeployment((request) -> request.applicationId(this.applicationId)
					.environmentId(this.environmentId)
					.deploymentNumber(this.deploymentNumber))
				.state();
			if (state == DeploymentState.ROLLED_BACK) {
				throw new IllegalStateException("AppConfig deployment rolled back for " + this.applicationName);
			}
			return state == DeploymentState.COMPLETE;
		});
		LOG.info("AppConfig configuration deployed for {}", this.applicationName);
	}

	@Override
	public void close() {
		try {
			if (this.applicationId != null) {
				if (this.deploymentNumber != null) {
					try {
						this.appConfig.stopDeployment((request) -> request.applicationId(this.applicationId)
							.environmentId(this.environmentId)
							.deploymentNumber(this.deploymentNumber));
					}
					catch (RuntimeException ex) {
						LOG.debug("Could not stop deployment (it may already be complete)", ex);
					}
				}
				boolean deleted = false;
				for (int attempt = 1; attempt <= 5 && !deleted; attempt++) {
					try {
						this.appConfig.deleteApplication((request) -> request.applicationId(this.applicationId));
						deleted = true;
					}
					catch (RuntimeException ex) {
						if (attempt == 5) {
							LOG.warn("Could not delete AppConfig application {}; delete it manually if needed",
									this.applicationName, ex);
						}
						else {
							sleep(Duration.ofSeconds(2));
						}
					}
				}
			}
			if (this.deploymentStrategyId != null) {
				try {
					this.appConfig.deleteDeploymentStrategy(
							(request) -> request.deploymentStrategyId(this.deploymentStrategyId));
				}
				catch (RuntimeException ex) {
					LOG.warn("Could not delete AppConfig deployment strategy {}; delete it manually if needed",
							this.deploymentStrategyName, ex);
				}
			}
		}
		finally {
			if (this.appConfig != null) {
				this.appConfig.close();
			}
			if (this.emulator != null) {
				this.emulator.stop();
			}
			deleteRecursively(this.tempDirectory);
		}
	}

	private static void awaitHealthy(URI endpoint, SslMaterial ssl) {
		HttpClient client = ssl.javaHttpClient();
		URI health = endpoint.resolve("/__admin/health");
		await().atMost(Duration.ofMinutes(1)).pollInterval(Duration.ofMillis(500)).until(() -> {
			try {
				HttpResponse<Void> response = client.send(HttpRequest.newBuilder(health).GET().build(),
						HttpResponse.BodyHandlers.discarding());
				return response.statusCode() == 200;
			}
			catch (IOException ex) {
				return false;
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return false;
			}
		});
	}

	private static boolean copyFromContainer(GenericContainer<?> container, String containerPath, Path target) {
		try {
			Files.deleteIfExists(target);
			container.copyFileFromContainer(containerPath, target.toString());
			return Files.isRegularFile(target);
		}
		catch (Exception ex) {
			return false;
		}
	}

	private static AwsCredentials readCredentials(Path credentialsFile) throws IOException {
		Map<String, String> values = new HashMap<>();
		for (String line : Files.readAllLines(credentialsFile)) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("[")) {
				continue;
			}
			int separator = trimmed.indexOf('=');
			if (separator > 0) {
				values.put(trimmed.substring(0, separator).trim(), trimmed.substring(separator + 1).trim());
			}
		}
		String accessKeyId = values.get("aws_access_key_id");
		String secretAccessKey = values.get("aws_secret_access_key");
		if (accessKeyId == null || secretAccessKey == null) {
			throw new IllegalStateException("No AWS credentials found in " + credentialsFile);
		}
		return AwsBasicCredentials.create(accessKeyId, secretAccessKey);
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return "us-east-1";
	}

	private static void sleep(Duration duration) {
		try {
			Thread.sleep(duration.toMillis());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private static void deleteRecursively(Path directory) {
		if (directory == null || !Files.exists(directory)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(directory)) {
			paths.sorted(Comparator.reverseOrder()).forEach((path) -> {
				try {
					Files.deleteIfExists(path);
				}
				catch (IOException ex) {
					LOG.debug("Could not delete {}", path, ex);
				}
			});
		}
		catch (IOException ex) {
			LOG.debug("Could not clean up {}", directory, ex);
		}
	}

	/**
	 * TLS material for the emulator's self-signed certificate.
	 */
	private record SslMaterial(TrustManagerFactory trustManagerFactory, SSLContext sslContext) {

		static SslMaterial from(Path truststore) throws Exception {
			KeyStore keyStore = KeyStore.getInstance("PKCS12");
			try (InputStream in = Files.newInputStream(truststore)) {
				keyStore.load(in, null);
			}
			TrustManagerFactory trustManagerFactory = TrustManagerFactory
				.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			trustManagerFactory.init(keyStore);
			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
			return new SslMaterial(trustManagerFactory, sslContext);
		}

		SdkHttpClient sdkHttpClient() {
			return ApacheHttpClient.builder()
				.tlsTrustManagersProvider(this.trustManagerFactory::getTrustManagers)
				.build();
		}

		HttpClient javaHttpClient() {
			return HttpClient.newBuilder().sslContext(this.sslContext).build();
		}

	}

}
