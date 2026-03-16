package com.joshlong.mogul.api.jobs;

import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.Map;

abstract class ResultUtils {

	static Map<String, Object> validate(Map<String, Object> stringObjectMap) {
		var newMap = new HashMap<>(stringObjectMap == null ? new HashMap<>() : stringObjectMap);
		Assert.state(newMap.containsKey(Job.MOGUL_ID_KEY), "you must specify a key for the mogul");
		return newMap;
	}

}
