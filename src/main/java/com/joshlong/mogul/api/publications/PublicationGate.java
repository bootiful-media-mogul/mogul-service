package com.joshlong.mogul.api.publications;

public interface PublicationGate {

	/**
	 * @param resume run this once whatever the gate was waiting for has happened
	 * @return true if this gate took the attempt, in which case nobody else may run it
	 */
	boolean defer(PublicationService.PublicationAttempt<?> attempt, Runnable resume);

}
