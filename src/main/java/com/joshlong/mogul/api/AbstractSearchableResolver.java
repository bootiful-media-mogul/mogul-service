package com.joshlong.mogul.api;

import com.joshlong.mogul.api.utils.TypeUtils;
import org.jspecify.annotations.NonNull;

public abstract class AbstractSearchableResolver<T extends Searchable> extends AbstractDomainResolver<Searchable, T>
		implements SearchableResolver<T> {

	protected final String type;

	protected AbstractSearchableResolver(Class<T> entityClass) {
		super(entityClass);
		this.type = this.type(entityClass);
	}

	public String getType() {
		return type;
	}

	private String type(@NonNull Class<?> clzz) {
		return TypeUtils.typeName(clzz);
	}

}
