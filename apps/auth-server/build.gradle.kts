plugins {
	java
	id("org.springframework.boot") version "4.0.6"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.secureone"
version = "1.1.0"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

springBoot {
	// META-INF/build-info.properties: the release version for /api/info and /actuator/info.
	buildInfo()
}

repositories {
	mavenCentral()
	// OpenSAML (used by Spring Security's SAML 2.0 service provider) is published only here.
	maven("https://build.shibboleth.net/maven/releases/") {
		content {
			includeGroupByRegex("org[.]opensaml.*")
			includeGroupByRegex("net[.]shibboleth.*")
		}
	}
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("io.micrometer:micrometer-registry-prometheus")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-authorization-server")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.security:spring-security-saml2-service-provider")
	implementation("org.opensaml:opensaml-saml-api")
	implementation("org.opensaml:opensaml-saml-impl")
	// Self-signed SAML SP signing certificate (already on the classpath through OpenSAML).
	implementation("org.bouncycastle:bcpkix-jdk18on:1.77")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
	implementation("org.flywaydb:flyway-database-postgresql")
	// Tenant configuration files (configuration as code).
	implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
	implementation("com.googlecode.libphonenumber:libphonenumber:8.13.50")
	implementation("software.amazon.awssdk:sns:2.29.51")
	implementation("dev.samstevens.totp:totp:1.7.1")
	implementation("com.yubico:webauthn-server-core:2.5.2")
	implementation("org.springframework.session:spring-session-data-redis")
	compileOnly("org.projectlombok:lombok")
	runtimeOnly("org.postgresql:postgresql")
	annotationProcessor("org.projectlombok:lombok")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-authorization-server-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testCompileOnly("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.withType<JavaCompile> {
	options.compilerArgs.add("-parameters")
}
