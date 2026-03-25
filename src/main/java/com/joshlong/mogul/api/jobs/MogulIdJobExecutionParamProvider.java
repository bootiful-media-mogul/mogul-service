package com.joshlong.mogul.api.jobs;

import com.joshlong.mogul.api.mogul.MogulService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Supplier;

@Component
class MogulIdJobExecutionParamProvider implements JobExecutionParamProvider {

	private final MogulService mogulService;

	MogulIdJobExecutionParamProvider(MogulService mogulService) {
		this.mogulService = mogulService;
	}

	@Override
	public boolean supports(Job job) {
		return true;
	}

	@Override
	public Map<String, Supplier<Object>> prepare(JobExecution jobExecution) {
		return Map.of(Job.MOGUL_ID_KEY, jobExecution::mogulId);
	}

}
