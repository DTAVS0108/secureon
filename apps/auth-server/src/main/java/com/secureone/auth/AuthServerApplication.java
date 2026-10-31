package com.secureone.auth;

import com.secureone.auth.upgrade.MigrateOnly;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AuthServerApplication {

	public static void main(String[] args) {
		// The /admin proxy (ConsoleProxyFilter) passes the public Host header on to the console, which
		// java.net.http refuses unless allowed before its first use.
		if (System.getProperty("jdk.httpclient.allowRestrictedHeaders") == null) {
			System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
		}
		StartupWait.forDependencies();
		if (MigrateOnly.requested()) {
			System.exit(MigrateOnly.run(args));
		}
		SpringApplication.run(AuthServerApplication.class, args);
	}

}
