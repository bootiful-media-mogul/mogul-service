package com.joshlong.mogul.api.jobs;

import org.springframework.stereotype.Component;

import java.util.Map;

/* useful for testing */
@Component
class FailingJob implements Job {

	@Override
	public Result run(Map<String, Object> context) throws Exception {
		Thread.sleep(10_000);
		return Result.error(context, new RuntimeException("oops"));
	}

}
