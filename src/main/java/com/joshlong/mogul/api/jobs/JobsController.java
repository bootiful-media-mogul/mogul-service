package com.joshlong.mogul.api.jobs;

import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@Controller
class JobsController {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final Jobs jobs;

	private final MogulService mogulService;

	private final ManagedFileService managedFileService;

	JobsController(Jobs jobs, MogulService mogulService, ManagedFileService managedFileService) {
		this.jobs = jobs;
		this.mogulService = mogulService;
		this.managedFileService = managedFileService;
	}

	private Map<String, Supplier<Object>> buildMapOfSuppliers(Map<String, Object> map) {
		var m = new HashMap<String, Supplier<Object>>();
		for (var k : map.keySet()) {
			m.put(k, () -> map.get(k));
		}
		return m;
	}

	@MutationMapping
	boolean launchJob(@Argument String jobName, @Argument String contextAsJson) throws JobException {
		// @formatter:off
        var typeReference = new ParameterizedTypeReference<Map<String,Object>>() {};
        // @formatter:on
		if (JsonUtils.read(contextAsJson, typeReference) instanceof Map<String, Object> context) {
			var mogulId = this.mogulService.getCurrentMogul().id();
			context.putIfAbsent(Job.MOGUL_ID_KEY, mogulId);
			var ctx = this.buildMapOfSuppliers(context);
			var je = this.jobs.prepare(mogulId, jobName, ctx);
			this.jobs.launch(mogulId, je.id(), ctx);
		}

		return true;
	}

	@QueryMapping
	Collection<JobView> jobs() {
		var mogul = this.mogulService.getCurrentMogul().id();
		return this.jobs //
			.jobs()
			.entrySet()//
			.stream() //
			.map(stringJobEntry -> this.buildJobView(mogul, stringJobEntry))
			.toList();
	}

	private JobView buildJobView(Long mogul, Map.Entry<String, Job> jobEntry) {
		// todo could we have some convention by which to
		// default values from the Settings object?
		var preparedJob = this.jobs.prepare(mogul, jobEntry.getKey(), Map.of());
		var contextAttributes = this.paramMapToParamsCollection(preparedJob.context());
		return new JobView(jobEntry.getKey(), contextAttributes,
				this.requiredContextAttributesFrom(jobEntry.getValue()));
	}

	private String[] requiredContextAttributesFrom(Job execution) {
		if (execution == null || execution.requiredContextAttributes() == null) {
			return new String[0];
		}
		return execution.requiredContextAttributes()
			.stream()
			.filter(attributeName -> !attributeName.equals(Job.MOGUL_ID_KEY))
			.toArray(String[]::new);
	}

	private Collection<JobParam> paramMapToParamsCollection(Map<String, JobExecutionParam> paramMap) {
		var jobParamArrayList = new ArrayList<JobParam>();
		paramMap.forEach((paramName, jobExecutionParam) -> {
			jobParamArrayList.add(new JobParam(paramMap.get(paramName).name(), jobExecutionParam.jsonValue()));
		});
		return jobParamArrayList;
	}

	record JobView(String name, Collection<JobParam> contextAttributes, String[] requiredContextAttributes) {
	}

	record JobParam(String name, String value) {
	}

}
