package com.joshlong.mogul.api.jobs;

public class JobException extends Exception {

	private final String message;

	JobException(String message) {
		super(message);
		this.message = message;
	}

	JobException(String message, Throwable cause) {
		super(message, cause);
		this.message = message;
	}

	public String getMessage() {
		return this.message;
	}

}
