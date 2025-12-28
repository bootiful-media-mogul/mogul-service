package com.joshlong.mogul.api.jobs;

import org.springframework.stereotype.Component;

import java.util.Map;

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

	@Override
	public Result run(Map<String, Object> context) throws Exception {
		IO.println("hello, " + context.get("name"));
		return Result.ok(context);
	}

}
