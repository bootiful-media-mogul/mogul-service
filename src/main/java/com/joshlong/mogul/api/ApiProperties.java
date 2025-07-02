package com.joshlong.mogul.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.File;
import java.net.URI;

@ConfigurationProperties(prefix = "mogul")
public record ApiProperties(Aws aws, ManagedFiles managedFiles, Transcriptions transcriptions, Podcasts podcasts,
		Notifications notifications, Settings settings, boolean debug) {

	public record Notifications(Ably ably) {

		public record Ably(String apiKey) {
		}

	}

	public record Transcriptions(File root) {
	}

	public record Settings(String password, String salt) {
	}

	public record Aws(String accessKey, String accessKeySecret, String region, Cloudfront cloudfront) {
		public record Cloudfront(URI domain) {
		}
	}

	public record ManagedFiles(S3 s3) {

		public record S3(String bucket) {
		}
	}

	public record Podcasts(Producer production) {

		public record Producer(Amqp amqp) {

			public record Amqp(String requests, String replies) {
			}

		}
	}
}