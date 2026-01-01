package com.joshlong.mogul.api.jobs;

import java.util.Map;

record EventPublicationBackedJobsRunnerEvent(String key, String jobName, Map<String, Object> context) {
}
