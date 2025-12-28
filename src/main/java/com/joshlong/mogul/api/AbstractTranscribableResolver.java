package com.joshlong.mogul.api;

public abstract class AbstractTranscribableResolver<T extends Transcribable>
		extends AbstractDomainResolver<Transcribable, T> implements TranscribableResolver<T> {

	protected AbstractTranscribableResolver(Class<T> entityClass) {
		super(entityClass);
	}

}
