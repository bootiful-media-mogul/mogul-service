package com.joshlong.mogul.api.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.util.FileCopyUtils;

import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

class CliHealthIndicator implements HealthIndicator {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final AtomicReference<Health> health = new AtomicReference<>();

	private final String[] command;

	private final String name;

	CliHealthIndicator(String[] command, String name) {
		this.command = command;
		this.name = name;
	}

	@Override
	public Health health() {
		if (this.health.get() == null) {
			this.health.set(computeHealth());
		}
		return this.health.get();
	}

	private Health computeHealth() {
		try {
			var process = new ProcessBuilder().command(this.command).start();
			var output = FileCopyUtils.copyToString(new InputStreamReader(process.getInputStream()));
			var error = FileCopyUtils.copyToString(new InputStreamReader(process.getErrorStream()));
			var exit = process.waitFor();
			var health = exit == 0 ? Health.up() : Health.down();
			return health //
				.withDetail(this.name + "-output", output) //
				.withDetail(this.name + "error", error) //
				.build();
		} //
		catch (Throwable throwable) {
			this.log.warn("could not capture the health for the command {}", Arrays.toString(this.command), throwable);
		}
		return Health.unknown().build();
	}

}
