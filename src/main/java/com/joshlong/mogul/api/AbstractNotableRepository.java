package com.joshlong.mogul.api;

public abstract class AbstractNotableRepository<T extends Notable> extends AbstractDomainRepository<Notable, T>
		implements NotableRepository<T> {

	protected AbstractNotableRepository(Class<T> entityClass) {
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
