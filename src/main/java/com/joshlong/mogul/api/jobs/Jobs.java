package com.joshlong.mogul.api.jobs;

import java.util.Map;

public interface Jobs {

	Map<String, Job> jobs();

	void launch(Long mogulId, String jobName, Map<String, Object> context) throws JobException;

}
