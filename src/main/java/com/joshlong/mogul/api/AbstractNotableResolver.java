package com.joshlong.mogul.api;

public abstract class AbstractNotableResolver<T extends Notable> extends AbstractDomainResolver<Notable, T>
		implements NotableResolver<T> {

	protected AbstractNotableResolver(Class<T> entityClass) {
		super(entityClass);
	}

}

