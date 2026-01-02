package com.joshlong.mogul.api;

public abstract class AbstractSearchableResolver<T extends Searchable> extends AbstractDomainResolver<Searchable, T>
		implements SearchableResolver<T> {

	protected AbstractSearchableResolver(Class<T> entityClass) {
		super(entityClass);
	}

}

