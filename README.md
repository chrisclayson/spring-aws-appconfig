# AWS AppConfig configuration for Spring Boot

A Spring Boot config-data provider that reads application configuration from **AWS AppConfig** instead of (or in addition to) local `application.yaml`, `application.json` and `application.properties` files. When Spring Cloud Context is present it can also refresh the running application when the AppConfig configuration changes.

It plugs into Spring Boot's config-data pipeline (`ConfigDataLocationResolver` + `ConfigDataLoader`), which means AppConfig values are real Spring `PropertySource`s: they work with `@Value`, `@ConfigurationProperties`, placeholders and profile handling just like local files.

```
spring.config.import=aws-appconfig:orders-api/prod/orders-api
```

Tested with Spring Boot 3.5.16 and 4.1.2 on Java 17.

Two artifacts are built from the same source tree:

| Spring Boot | Artifact |
| --- | --- |
| 3.x | `uk.org.clayson:aws-appconfig-config` |
| 4.x | `uk.org.clayson:aws-appconfig-config-boot4` |

Use the artifact that matches your Spring Boot generation. The split exists because Spring Boot 4 moved
the bootstrap context classes to a different package, which changes the bytecode that registers a custom
`AppConfigClientFactory`; the application-facing behavior is identical. The Boot 3 artifact is built and
tested against 3.5.16 and should work with other 3.x releases that provide a matching Spring Cloud version.

---

## How it works

1. Spring Boot sees an `aws-appconfig:` entry in `spring.config.import`.
2. `AppConfigConfigDataLocationResolver` parses the location and any `appconfig.*` bootstrap properties.
3. `AppConfigConfigDataLoader` starts an AppConfig **configuration session** and calls `GetLatestConfiguration` through the AWS SDK v2 data-plane client.
4. The payload is parsed with the same YAML/properties parsers Spring Boot uses locally, and inserted as config data immediately after the document that declared the import.
5. If Spring Cloud Context is on the classpath, `AppConfigRefresher` polls the same configuration and calls `ContextRefresher.refresh()` when it changes. Without Spring Cloud Context the configuration is loaded once at startup.

Because imported config data has higher precedence than the importing document, AppConfig values override values from local `application.*` files. OS environment variables, system properties and command-line arguments still override AppConfig, which keeps the usual Spring Boot precedence rules intact.

The resolver and loader are registered in `META-INF/spring.factories`; refresh uses a regular Spring Boot auto-configuration. No `@Enable...` annotation or manual bean registration is required.

---

## Usage

### 1. Add the dependency

```kotlin
dependencies {
    // Spring Boot 3.x
    implementation("uk.org.clayson:aws-appconfig-config:0.2.0-SNAPSHOT")

    // Spring Boot 4.x instead:
    // implementation("uk.org.clayson:aws-appconfig-config-boot4:0.2.0-SNAPSHOT")
}
```

```xml
<!-- Spring Boot 3.x -->
<dependency>
    <groupId>uk.org.clayson</groupId>
    <artifactId>aws-appconfig-config</artifactId>
    <version>0.2.0-SNAPSHOT</version>
</dependency>

<!-- Spring Boot 4.x: use aws-appconfig-config-boot4 instead -->
```

### 2. Import the configuration

In `application.properties` (or YAML):

```properties
spring.config.import=aws-appconfig:orders-api/prod/orders-api
appconfig.region=eu-west-1
```

Or, without any local config file, through environment variables:

```bash
export SPRING_CONFIG_IMPORT=aws-appconfig:orders-api/prod/orders-api
export APPCONFIG_REGION=eu-west-1
java -jar orders-api.jar
```

`spring.config.import` itself may be supplied as a system property, an environment variable (`SPRING_CONFIG_IMPORT`) or a command-line argument, so an application can run with **no** local `application.*` file at all.

### 3. Read the values as usual

```java
@ConfigurationProperties(prefix = "orders")
public record OrderProperties(String endpoint, Duration timeout) {
}
```

Given this AppConfig payload:

```yaml
orders:
  endpoint: https://orders.example.com
  timeout: 2s
```

the record above binds as expected.

---

## Location syntax

```
aws-appconfig:<application>[/<environment>[/<configuration-profile>]]
```

| Location | Application | Environment | Configuration profile |
| --- | --- | --- | --- |
| `aws-appconfig:orders` | `orders` | `default` | `orders` |
| `aws-appconfig:orders/prod` | `orders` | `prod` | `orders` |
| `aws-appconfig:orders/prod/orders-api` | `orders` | `prod` | `orders-api` |
| `optional:aws-appconfig:orders/prod` | `orders` | `prod` | `orders` |

Empty segments fall back to the bootstrap properties:

```
aws-appconfig:orders//orders-api   # environment comes from appconfig.environment
```

Use the `optional:` prefix if a missing configuration should not fail startup. Note that this only ignores *not found* errors; set `appconfig.fail-fast=false` to ignore every read failure.

Multiple imports can be combined and later imports take precedence:

```properties
spring.config.import=aws-appconfig:shared/prod/shared,aws-appconfig:orders-api/prod/orders-api
```

---

## Bootstrap properties

These properties configure the connection. They are read from the initial Spring `Environment`, so they can come from environment variables, system properties, command-line arguments or a local file (if one exists). They must **not** be supplied through AppConfig itself, because the connection has to be established before AppConfig content can be read.

| Property | Default | Description |
| --- | --- | --- |
| `appconfig.application` | – | AppConfig application name (can come from the location instead) |
| `appconfig.environment` | `default` | AppConfig environment name |
| `appconfig.profile` | application name | AppConfig configuration profile name (not a Spring profile) |
| `appconfig.region` | SDK default chain | AWS region |
| `appconfig.endpoint` | – | Custom endpoint, e.g. LocalStack (`http://localhost:4566`) |
| `appconfig.content-type` | detected | `yaml`, `json` or `properties` |
| `appconfig.minimum-poll-interval` | `15s` | Minimum poll interval for the AppConfig session (minimum 15s) |
| `appconfig.prefix` | – | Prefix added to every loaded property name |
| `appconfig.fail-fast` | `true` | Whether read failures stop startup |

Relaxed binding applies to environment variables, for example `APPCONFIG_ENVIRONMENT`, `APPCONFIG_PROFILE`, `APPCONFIG_REGION`, `APPCONFIG_CONTENT_TYPE`.

---

## Content formats

AWS AppConfig freeform hosted configurations can contain YAML, JSON, properties or arbitrary text.

* If `appconfig.content-type` is set, it wins.
* Otherwise the `Content-Type` returned by AppConfig is used:
  * `application/json` → JSON
  * `application/x-yaml`, `application/yaml`, `text/yaml` → YAML
  * `text/plain` → properties (the conventional content type for flat key/value data)
  * anything else → YAML
* JSON is parsed with the YAML parser because JSON is a subset of YAML; this also avoids an extra Jackson dependency.

Nested YAML/JSON structures are flattened by Spring Boot (`orders.endpoint`) and can be prefixed:

```properties
appconfig.prefix=orders
```

```yaml
endpoint: https://orders.example.com
```

becomes `orders.endpoint`.

This provider targets **freeform** configurations. AppConfig **feature flags** are a different content format and are not mapped to Spring properties.

---

## AWS setup

The application needs permission for the two AppConfig data-plane actions:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "appconfig:StartConfigurationSession",
        "appconfig:GetLatestConfiguration"
      ],
      "Resource": "arn:aws:appconfig:eu-west-1:123456789012:application/orders-api/environment/prod/configuration/orders-api"
    }
  ]
}
```

Credentials and (when `appconfig.region` is not set) the region are resolved with the standard AWS SDK v2 default provider chain: environment variables, shared credentials/config files, container credentials, EC2/ECS instance roles, SSO, and so on.

Remember that a configuration must be **deployed** to the environment; otherwise the data plane returns an empty payload or `ResourceNotFoundException`.

---

## Customising the AWS client

The default client uses `AppConfigDataClient` with default credentials. To customise credentials, use a different SDK configuration, or inject a fake in tests, register an `AppConfigClientFactory` in the Spring Boot `BootstrapRegistry` before the application runs:

```java
public static void main(String[] args) {
    SpringApplication application = new SpringApplication(MyApplication.class);
    application.addBootstrapRegistryInitializer((registry) ->
        registry.register(AppConfigClientFactory.class,
            (bootstrapContext) -> (connectionDetails) -> {
                AppConfigDataClient client = AppConfigDataClient.builder()
                    .credentialsProvider(ProfileCredentialsProvider.create("dev"))
                    .build();
                return new AwsAppConfigContentClient(client, connectionDetails); // public constructor for a pre-built SDK client
            }));
    application.run(args);
}
```

The nested lambdas work on both Spring Boot 3 and 4 (`InstanceSupplier` itself moved packages in Boot 4). The factory runs during environment preparation, before the application context exists, so it cannot rely on Spring beans. It is reused by the runtime refresher, so custom credentials and endpoints apply to polling as well.

For tests, return a simple lambda instead of an AWS client:

```java
application.addBootstrapRegistryInitializer((registry) ->
    registry.register(AppConfigClientFactory.class,
        (bootstrapContext) -> (details) -> () -> new AppConfigContent(
            "greeting.message: hello", "application/x-yaml", "1")));
```

The integration test in this repository does exactly that (`AppConfigConfigDataIntegrationTest`).

---

## Runtime refresh (Spring Cloud Context)

Runtime refresh is **optional but preferred**: when Spring Cloud Context is on the classpath and a
`ContextRefresher` bean is available, the provider polls AppConfig in the background and refreshes the
application context when the hosted configuration changes. Without Spring Cloud Context everything still
works, but configuration is only loaded at startup.

### Enable it

Add Spring Cloud Context, using the release train that matches your Spring Boot version (in a typical
Spring Cloud application it is already present):

```kotlin
implementation("org.springframework.cloud:spring-cloud-context:<spring-cloud-version>")
```

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-context</artifactId>
    <version>${spring-cloud.version}</version>
</dependency>
```

Use the Spring Cloud release train that matches your Spring Boot version, or import the
`spring-cloud-dependencies` BOM and omit the version. In a typical Spring Cloud application the dependency
is already present.

No annotation or extra bean is required: `AppConfigRefreshAutoConfiguration` activates automatically when
the `ContextRefresher` bean is available. Refresh is enabled by default in that case and can be turned off
with:

```properties
appconfig.refresh.enabled=false
```

The poll interval defaults to 30 seconds and can be changed with:

```properties
appconfig.refresh.interval=45s
```

| Property | Default | Description |
| --- | --- | --- |
| `appconfig.refresh.enabled` | `true` when Spring Cloud Context is present | Whether to poll AppConfig and refresh the context |
| `appconfig.refresh.interval` | `30s` | Time between polls; keep it at or above `appconfig.minimum-poll-interval` (15s) |

### What happens on a change

1. `AppConfigRefresher` polls each imported configuration with a long-lived AppConfig data-plane session.
   An empty payload means "unchanged"; version labels are also compared before applying a change.
2. The `AppConfigPropertySource` is updated in place.
3. `ContextRefresher.refresh()` is called, which rebinds `@ConfigurationProperties` beans and refreshes
   `@RefreshScope` beans.

For singletons that are not refresh-scoped, use `@ConfigurationProperties` or a
`@RefreshScope` proxy. `@ConfigurationProperties` **records** are constructor-bound and cannot be mutated
by Spring Cloud's rebinder; use a mutable JavaBean or `@RefreshScope` if the value must change at runtime.

Polling reuses the same `AppConfigClientFactory` (and therefore the same credentials, endpoint and test
fake) that was used at startup. Spring Cloud's `ContextRefresher` re-runs environment post-processors; the
poller shares the already fetched payload with that reload, so a change is not downloaded twice and custom
client factories keep working. A failed poll is logged and the next poll continues.

The refresh auto-configuration is registered in
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Without a
`ContextRefresher` bean it backs off completely, so the provider remains usable as a startup-only
configuration source.

---

## Project layout

Classes are grouped by role under `uk.org.clayson.spring.appconfig`.

```
common/src/main/java/uk/org/clayson/spring/appconfig/
  autoconfigure/AppConfigRefreshAutoConfiguration.java
  client/AppConfigContentClient.java
  client/AwsAppConfigContentClient.java
  configdata/AppConfigBootstrapProperties.java
  configdata/AppConfigConfigDataLoader.java
  configdata/AppConfigConfigDataLocationResolver.java
  configdata/AppConfigConfigDataResource.java
  configdata/AppConfigRefreshContext.java
  exception/AppConfigException.java
  exception/AppConfigNotFoundException.java
  factory/AppConfigClientFactory.java
  factory/AwsAppConfigClientFactory.java
  model/AppConfigConnectionDetails.java
  model/AppConfigContent.java
  model/AppConfigContentType.java
  property/AppConfigPropertySource.java
  property/AppConfigPropertySourceLoader.java
  refresh/AppConfigRefreshProperties.java
  refresh/AppConfigRefresher.java
common/src/main/resources/META-INF/
  spring.factories                          resolver + loader registration
  spring/...AutoConfiguration.imports       refresh auto-configuration
  spring-configuration-metadata.json        IDE metadata for appconfig.*
common/src/test/java/uk/org/clayson/spring/appconfig/
  ...                                       unit + end-to-end tests in matching packages
boot3/build.gradle.kts                      Boot 3.x artifact: aws-appconfig-config
boot4/build.gradle.kts                      Boot 4.x artifact: aws-appconfig-config-boot4
example/
  docker/aws-emulator.Dockerfile            AWS Emulator image for the integration test
  src/main/java/.../ExampleApplication.java  simple example application
  src/main/java/.../GreetingProperties.java  values read from AppConfig
  src/main/resources/application.properties  local defaults overridden by AppConfig
  src/integrationTest/java/.../             AppConfig emulator/real-AWS end-to-end test
.github/workflows/ci.yml                    pull-request build and test
.github/workflows/publish.yml               post-merge Maven Central publication
gradle.properties                           version and POM metadata
LICENSE                                     MIT license
```

## Building

```bash
./gradlew test                                # both Spring Boot generations
./gradlew :aws-appconfig-config:test          # Boot 3.x only
./gradlew :aws-appconfig-config-boot4:test    # Boot 4.x only
./gradlew build                               # jars, sources, javadoc and tests
./gradlew publishToMavenLocal                 # local smoke test of the publications
```

## Integration test (example application)

`example/` contains a small Spring Boot application that binds `greeting.message` and
`greeting.source` from AppConfig. The end-to-end test:

1. starts **AWS Emulator** in Docker through Testcontainers (the first run builds the
   emulator image),
2. creates a temporary AppConfig application, environment, hosted configuration and
   zero-bake deployment strategy in the emulator,
3. starts the example application with
   `spring.config.import=aws-appconfig:<application>/<environment>/<profile>` and a custom
   `AppConfigClientFactory` that trusts the emulator certificate,
4. asserts that the AppConfig values override the local `application.properties` defaults,
5. deletes the temporary resources.

Run it with:

```bash
./gradlew :example:integrationTest
```

The default mode needs Docker, but no AWS account or credentials. The integration test is
not part of `./gradlew build`; the CI workflows run it explicitly.

### Run against real AWS (optional)

Set `APPCONFIG_IT_MODE=real` and provide credentials through the normal AWS SDK chain:

```bash
APPCONFIG_IT_MODE=real \
AWS_REGION=eu-west-1 \
AWS_PROFILE=my-sandbox \
./gradlew :example:integrationTest
```

`APPCONFIG_IT_REGION` can be used instead of `AWS_REGION`. Credentials can also be supplied
with `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` and `AWS_SESSION_TOKEN`. The test creates a
uniquely named temporary application and deployment strategy, deploys the configuration, runs
the example application, and then deletes both. Use a sandbox account.

Required IAM permissions for real mode:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "appconfig:CreateApplication",
        "appconfig:CreateEnvironment",
        "appconfig:CreateConfigurationProfile",
        "appconfig:CreateHostedConfigurationVersion",
        "appconfig:CreateDeploymentStrategy",
        "appconfig:StartDeployment",
        "appconfig:GetDeployment",
        "appconfig:StopDeployment",
        "appconfig:DeleteApplication",
        "appconfig:DeleteDeploymentStrategy",
        "appconfig:StartConfigurationSession",
        "appconfig:GetLatestConfiguration"
      ],
      "Resource": "*"
    }
  ]
}
```

## CI/CD and publishing

### Pull requests

`.github/workflows/ci.yml` runs when a pull request is opened, updated or reopened. It runs:

```bash
./gradlew clean build
```

which compiles and tests both the Spring Boot 3 and Spring Boot 4 modules, then runs the
AppConfig integration test against AWS Emulator.

### Merges to main

`.github/workflows/publish.yml` runs on every push to `main` (or `master`) and:

1. Derives a unique release version from `gradle.properties`, for example `0.2.0-42`
   (`<base>-<GitHub run number>`).
2. Builds and tests both modules with that version, and runs the AppConfig integration
   test against AWS Emulator.
3. Publishes both `aws-appconfig-config` and `aws-appconfig-config-boot4` in one
   aggregated, signed deployment to the Maven Central Portal.

A manual `workflow_dispatch` run can supply an explicit version. The default
`0.2.0-SNAPSHOT` in `gradle.properties` is only used for local builds; Maven Central
releases must not use `-SNAPSHOT`.

Required repository secrets:

| Secret | Description |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | Central Portal user token username |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal user token password |
| `GPG_SIGNING_KEY` | ASCII-armored private PGP key |
| `GPG_SIGNING_PASSWORD` | PGP key passphrase |

Before the first publish:

* Verify the `uk.org.clayson` namespace at [central.sonatype.com](https://central.sonatype.com/).
* Publish the public PGP key to a keyserver so Central can verify the signatures.
* Update the POM metadata in `gradle.properties` (`projectUrl`, SCM URLs and developer
  details) to match the real repository.

## Limitations

* Java 17+. Use `aws-appconfig-config` for Spring Boot 3.x and `aws-appconfig-config-boot4` for Spring Boot 4.x.
* Runtime refresh requires Spring Cloud Context; without it, configuration is loaded once at startup.
* `@ConfigurationProperties` records cannot be rebound by Spring Cloud's rebinder; use a mutable JavaBean or `@RefreshScope` for values that must change at runtime.
* AWS AppConfig feature-flag profiles are not supported.
* Only UTF-8 content is supported.

## License

MIT. See [LICENSE](LICENSE).
