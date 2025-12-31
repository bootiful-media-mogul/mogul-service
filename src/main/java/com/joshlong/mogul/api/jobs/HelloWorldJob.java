package com.joshlong.mogul.api.jobs;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * my idea is this: the client should list all the jobs in the system. on the server-side
 * we should inspect all the context parameters and render the right component to fill out
 * those context fields.
 *
 * <li><b>mogulId</b> - provided by default</li>
 * <li><b>podcastId</b> - the client should show a list of all the users podcasts</li>
 * <li><b>episodeId</b> - the client should show a list of all the users episodes (once a
 * podcast has been specified)</li>
 * <p>
 * in addition to the well-known context parameters, if there's a parameter we don't
 * recognize, then we should show a textbox.
 */
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
		Thread.sleep(5_000);
		this.log.info("stop: hello, {}", name);
		return Result.ok(context);
	}

}
