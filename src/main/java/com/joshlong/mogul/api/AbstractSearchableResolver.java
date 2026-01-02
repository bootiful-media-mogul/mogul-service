package com.joshlong.mogul.api;

public abstract class AbstractSearchableResolver<T extends Searchable, AGGREGATE>
		extends AbstractDomainResolver<Searchable, T> implements SearchableResolver<T, AGGREGATE> {

	protected AbstractSearchableResolver(Class<T> entityClass) {
		super(entityClass);
	}

}
