package com.joshlong.mogul.api.search;

import com.joshlong.mogul.api.Searchable;

public interface SearchableRepository<T extends Searchable> {

	T find(Long searchableId);

	boolean supports(Class<?> clazz);

	/**
	 * it makes sense to keep this responsibility at the repository level, since we may
	 * need to resolve the text by looking up a
	 * {@link com.joshlong.mogul.api.Transcribable transcribable}
	 */
	String text(Long searchableId);

}
