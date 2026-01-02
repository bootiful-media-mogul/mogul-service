package com.joshlong.mogul.api.jobs;

import java.util.Map;

// this is an implementation detail. do not make public
record JobRunEvent(String key, String jobName, Map<String, Object> context) {
}
