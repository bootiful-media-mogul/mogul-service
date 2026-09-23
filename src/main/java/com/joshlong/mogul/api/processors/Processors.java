package com.joshlong.mogul.api.processors;

import java.util.Map;

public interface Processors {

	/**
	 * asynchronously launch a processor
	 * @param processorId with a given processorId
	 * @param context and a map of parameters (that you know to convert well to JSON via
	 * Jackson 3)
	 * @throws Exception if for some reason the processor cannot be launched
	 */
	void process(String processorId, String correlationId, Map<String, Object> context) throws Exception;

}
