package com.joshlong.mogul.api;

public abstract class AbstractNotableResolver<T extends Notable> extends AbstractDomainResolver<Notable, T>
		implements NotableResolver<T> {

	protected AbstractNotableResolver(Class<T> entityClass) {
		super(entityClass);
	}

}
// public abstract class AbstractPublishableRepository<T extends Publishable>
// extends AbstractDomainRepository<Publishable, T> implements PublishableRepository<T> {
//
// protected AbstractPublishableRepository(Class<T> entityClass) {
// super(entityClass);
// }
//
// }
