package com.joshlong.mogul.api.utils;

import org.slf4j.LoggerFactory;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStreamReader;

public abstract class ProcessUtils {

	public static int runCommand(String... command) throws IOException, InterruptedException {
		var log = LoggerFactory.getLogger(ProcessUtils.class);
		var processBuilder = new ProcessBuilder(command).inheritIO();
		var process = processBuilder.start();
		var exit = process.waitFor();
		if (exit != 0) {
			try (var err = process.getErrorStream(); var inp = process.getInputStream()) {
				log.info(FileCopyUtils.copyToString(new InputStreamReader(err)));
				log.info(FileCopyUtils.copyToString(new InputStreamReader(inp)));
			}
		}
		return exit;
	}

}
