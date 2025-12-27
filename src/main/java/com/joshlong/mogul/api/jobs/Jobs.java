package com.joshlong.mogul.api.jobs;

import java.util.Map;

/**
 * the client should list all the jobs in the system. on the server-side we should inspect
 * all the context parameters and render the right component to fill out those context
 * fields.
 *
 * <li><b>mogulId</b> - provided by default</li>
 * <li><b>podcastId</b> - the client should show a list of all the users podcasts</li>
 * <li><b>episodeId</b> - the client should show a list of all the users episodes (once a
 * podcast has been specified)</li>
 * <p>
 * in addition to the well-known context parameters, if there's a parameter we don't
 * recognize then we should show a textbox.
 *
 * <p>
 * <B>TODO</B> eventually we'll adapt Spring Cloud Task or Spring Batch or JobRunr or
 * something like that as the foundational layer to handle persistence. I don't really
 * want to get into that business.
 * </P>
 *
 **/
interface Jobs {

	Map<String, Job> jobs();

	void launch(String jobName, Map<String, Object> context) throws JobLaunchException;

}
