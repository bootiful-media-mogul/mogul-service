package com.joshlong.mogul.api.jobs;

import java.util.Map;

public record MogulJobLaunchEvent(String jobName, Map<String, Object> context) {
}
