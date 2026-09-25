plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "11.0.10"
  kotlin("plugin.spring") version "2.4.10"
  kotlin("plugin.jpa") version "2.4.10"
  id("au.com.dius.pact") version "4.6.5"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

// Matches the conventions used by the hmpps-person-record provider (see its
// pact_provider_verification.yml / record_deployment.yml on add-pact-record-deployment-step):
// - broker auth via username/password secrets, not a bearer token
// - pacticipant version pinned to the commit SHA so it lines up with "deployed"/"mainBranch" selectors
// - branch recorded via GITHUB_BRANCH so provider verification can target a specific consumer branch
pact {
  publish {
    pactDirectory = layout.buildDirectory.dir("pacts").get().asFile
    pactBrokerUrl = System.getenv("PACT_BROKER_URL") ?: "https://pact-broker-prod.apps.live-1.cloud-platform.service.justice.gov.uk"
    pactBrokerUsername = System.getenv("HMPPS_PACT_BROKER_USERNAME")
    pactBrokerPassword = System.getenv("HMPPS_PACT_BROKER_PASSWORD")
    consumerVersion = System.getenv("GITHUB_SHA") ?: "local"
    consumerBranch = System.getenv("GITHUB_BRANCH") ?: "local"
    tags = listOfNotNull(System.getenv("GITHUB_BRANCH"))
  }
}

dependencies {
  // Spring
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:2.5.0")
  implementation("org.springframework.boot:spring-boot-starter-webclient")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")

  // Telem
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.31.1")

  // DB
  // Spring Boot 4 no longer auto-configures Flyway from flyway-core alone; the starter is required or migrations silently won't run at startup.
  implementation("org.springframework.boot:spring-boot-starter-flyway")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")
  runtimeOnly("org.postgresql:postgresql")

  // AWS
  implementation("software.amazon.awssdk:secretsmanager")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:7.4.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

  // Open API
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
  // Spatial PDF text extraction for the court-register engine.
  implementation("org.apache.pdfbox:pdfbox:3.0.8")
  implementation("com.google.guava:guava:33.7.1-jre")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:2.5.0")
  testImplementation("org.springframework.boot:spring-boot-webtestclient")
  testImplementation("io.jsonwebtoken:jjwt-impl:0.13.0")
  testImplementation("io.jsonwebtoken:jjwt-jackson:0.13.0")
  testImplementation("org.wiremock:wiremock-standalone:3.13.2")
  testImplementation("au.com.dius.pact.consumer:junit5:4.7.0")
  testImplementation("org.awaitility:awaitility-kotlin:4.3.0")
  testImplementation("org.testcontainers:testcontainers:2.0.5")
  testImplementation("org.testcontainers:localstack:1.21.4")
  testImplementation("org.testcontainers:postgresql:1.21.4")
  testImplementation("org.testcontainers:junit-jupiter:1.21.4")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.48") {
    exclude(group = "io.swagger.core.v3")
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_24
  targetCompatibility = JavaVersion.VERSION_24
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24
    // Kotlin 2.2+ changed where annotations on constructor properties default-target;
    // restore the prior behaviour so JPA/validation/Jackson annotations land correctly.
    compilerOptions.freeCompilerArgs.add("-Xannotation-default-target=param-property")
  }
}

// Keep sensitive, local-only tests (e.g. extraction over real PDFs) out of the CI build.
tasks.named<Test>("test") {
  useJUnitPlatform {
    excludeTags("local")
  }
}

tasks.register<Test>("localTest") {
  description = "Runs local-only tests (e.g. extraction over sensitive sample PDFs)."
  group = "verification"
  testClassesDirs = sourceSets["test"].output.classesDirs
  classpath = sourceSets["test"].runtimeClasspath
  useJUnitPlatform {
    includeTags("local")
  }
  System.getProperty("extractionSampleDir")?.let { systemProperty("extractionSampleDir", it) }
  shouldRunAfter("test")
}

tasks.register<Test>("pactTest") {
  description = "Runs consumer-side Pact tests"
  group = "verification"
  testClassesDirs = sourceSets["test"].output.classesDirs
  classpath = sourceSets["test"].runtimeClasspath
  useJUnitPlatform()
  filter {
    includeTestsMatching("*PactTest")
    isFailOnNoMatchingTests = false
  }
  shouldRunAfter("test")
}
