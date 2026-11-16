plugins {
    `java-library`
}

group = "uk.org.clayson"
version = providers.gradleProperty("version").getOrElse("0.2.0-SNAPSHOT")

val springBootVersion = "3.5.16"
val awsSdkVersion = "2.59.0"
val testcontainersVersion = "2.0.5"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation(project(":aws-appconfig-config"))
    implementation("org.springframework.boot:spring-boot-starter")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.awaitility:awaitility")
    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("software.amazon.awssdk:appconfig:$awsSdkVersion")
    testImplementation("software.amazon.awssdk:appconfigdata:$awsSdkVersion")
    testImplementation("software.amazon.awssdk:apache-client:$awsSdkVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

val integrationTest: SourceSet = sourceSets.create("integrationTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
    java.srcDir("src/integrationTest/java")
    resources.srcDir("src/integrationTest/resources")
}

configurations.named(integrationTest.implementationConfigurationName) {
    extendsFrom(configurations.testImplementation.get())
}

configurations.named(integrationTest.runtimeOnlyConfigurationName) {
    extendsFrom(configurations.testRuntimeOnly.get())
}

tasks.register<Test>("integrationTest") {
    description = "Runs the example app against AWS AppConfig in AWS Emulator (or real AWS)."
    group = "verification"
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    useJUnitPlatform()
    systemProperty("awsEmulatorDockerfile",
        layout.projectDirectory.file("docker/aws-emulator.Dockerfile").asFile.absolutePath)
    maxHeapSize = "1g"
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}

tasks.test {
    useJUnitPlatform()
}
