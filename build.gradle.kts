plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "9.7.1"
  kotlin("plugin.spring") version "2.3.21"
  kotlin("plugin.jpa") version "2.3.21"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  // Spring
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:1.8.2")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")

  // Telem
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.28.1")

  // DB
  runtimeOnly("org.flywaydb:flyway-database-postgresql")
  runtimeOnly("org.postgresql:postgresql")

  // AWS
  implementation("software.amazon.awssdk:secretsmanager")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:5.6.3")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

  // Open API
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
  // Spatial PDF text extraction for the court-register engine.
  implementation("org.apache.pdfbox:pdfbox:3.0.7")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:1.8.2")
  testImplementation("io.jsonwebtoken:jjwt-impl:0.13.0")
  testImplementation("io.jsonwebtoken:jjwt-jackson:0.13.0")
  testImplementation("org.wiremock:wiremock-standalone:3.13.2")
  testImplementation("org.awaitility:awaitility-kotlin:4.3.0")
  testImplementation("org.testcontainers:testcontainers:2.0.5")
  testImplementation("org.testcontainers:localstack:1.21.4")
  testImplementation("org.testcontainers:postgresql:1.21.4")
  testImplementation("org.testcontainers:junit-jupiter:1.21.4")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.42") {
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
