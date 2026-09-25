package com.joshlong.mogul.api.processors;

import java.util.Map;

public interface Processors {

	/**
	 * asynchronously launch a processor. returns as soon as the request is on the wire;
	 * the work happens in another process and announces itself later with a
	 * {@link ProcessorCompletedEvent}.
	 */
	void process(String processorId, Map<String, Object> context) throws Exception;

}
