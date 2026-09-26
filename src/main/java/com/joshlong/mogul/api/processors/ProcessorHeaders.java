package com.joshlong.mogul.api.processors;

/**
 * the AMQP contract shared with the {@code processors} module. the two are separately
 * deployed and share no code, so each keeps its own copy of these names.
 */
abstract class ProcessorHeaders {

	static final String PROCESSOR_REQUESTS = "processor-requests";

	static final String PROCESSOR_REPLIES = "processor-replies";

	/**
	 * the name of the processor bean that should handle -- or that did handle -- this
	 * message.
	 */
	static final String PROCESSOR_ID = "processor-id";

	/**
	 * echoed back on the reply so that we can tie a response to the request we made.
	 */
	static final String PROCESSOR_REQUEST_ID = "processor-request-id";

}
