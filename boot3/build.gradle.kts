plugins {
    `java-library`
    `maven-publish`
    signing
    id("com.gradleup.nmcp")
}

group = "uk.org.clayson"
version = providers.gradleProperty("version").getOrElse("0.2.0-SNAPSHOT")

val springBootVersion = "3.5.16"
val springCloudVersion = "4.3.3"
val awsSdkVersion = "2.59.0"

repositories {
    mavenCentral()
}

dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    api("org.springframework.boot:spring-boot-starter:$springBootVersion")
    implementation("software.amazon.awssdk:appconfigdata:$awsSdkVersion")
    implementation("software.amazon.awssdk:apache-client:$awsSdkVersion")
    compileOnly("org.springframework.cloud:spring-cloud-context:$springCloudVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.cloud:spring-cloud-context:$springCloudVersion")
    testImplementation("org.awaitility:awaitility")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    withSourcesJar()
    withJavadocJar()
}

sourceSets {
    main {
        java.srcDir("$rootDir/common/src/main/java")
        resources.srcDir("$rootDir/common/src/main/resources")
    }
    test {
        java.srcDir("$rootDir/common/src/test/java")
        resources.srcDir("$rootDir/common/src/test/resources")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("AWS AppConfig Spring Boot 3 Configuration")
                description.set(
                    "Spring Boot 3.x config-data provider and optional runtime refresher for AWS AppConfig hosted configurations."
                )
                url.set(providers.gradleProperty("projectUrl"))
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/license/mit")
                    }
                }
                developers {
                    developer {
                        id.set(providers.gradleProperty("developerId"))
                        name.set(providers.gradleProperty("developerName"))
                        email.set(providers.gradleProperty("developerEmail"))
                    }
                }
                scm {
                    connection.set(providers.gradleProperty("projectScmConnection"))
                    developerConnection.set(providers.gradleProperty("projectScmDeveloperConnection"))
                    url.set(providers.gradleProperty("projectUrl"))
                }
            }
        }
    }
}

signing {
    val signingKey = providers.gradleProperty("signingKey").orNull
    val signingPassword = providers.gradleProperty("signingPassword").orNull
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
