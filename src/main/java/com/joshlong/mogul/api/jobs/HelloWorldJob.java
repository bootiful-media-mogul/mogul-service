package com.joshlong.mogul.api.jobs;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
class HelloWorldJob implements Job {

	private final Logger log = LoggerFactory.getLogger(getClass());

	@Override
	public @NonNull Set<String> requiredContextAttributes() {
		var params = new HashSet<>(Job.super.requiredContextAttributes());
		params.add("name");
		return params;
	}

	@Override
	public Result run(Map<String, Object> context) throws Exception {
		var name = (String) context.getOrDefault("name", "world");
		this.log.info("start: hello, {}", name);
		Thread.sleep(10_000);
		this.log.info("stop: hello, {}", name);
		return Result.ok(context);
	}

}
