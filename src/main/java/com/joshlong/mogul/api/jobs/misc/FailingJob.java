package com.joshlong.mogul.api.jobs.misc;

import com.joshlong.mogul.api.jobs.Job;
import com.joshlong.mogul.api.jobs.JobExecutionContext;
import com.joshlong.mogul.api.jobs.JobExecutionResult;
import org.springframework.stereotype.Component;

/* useful for testing */
@Component
class FailingJob implements Job {

	@Override
	public JobExecutionResult run(JobExecutionContext context) throws Exception {
		Thread.sleep(10_000);
		return JobExecutionResult.error(new RuntimeException("oops"));
	}

}
