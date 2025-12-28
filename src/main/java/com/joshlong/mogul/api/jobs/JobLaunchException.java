package com.joshlong.mogul.api.jobs;

public class JobLaunchException extends Exception {

	private final String message;

	JobLaunchException(String message) {
		super(message);
		this.message = message;
	}

	JobLaunchException(String message, Throwable cause) {
		super(message, cause);
		this.message = message;
	}

	public String getMessage() {
		return this.message;
	}

}
