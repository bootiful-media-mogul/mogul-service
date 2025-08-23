package com.joshlong.mogul.api.search;

import com.joshlong.mogul.api.Searchable;

public interface SearchableRepository<T extends Searchable, AGGREGATE> {

	T find(Long searchableId);

	boolean supports(Class<?> clazz);

	/**
	 * it makes sense to keep this responsibility at the repository level, since we may
	 * need to resolve the text by looking up a
	 * {@link com.joshlong.mogul.api.Transcribable transcribable}
	 */
	String text(Long searchableId);

	/**
	 * Returns the title for the result. for a
	 * {@link com.joshlong.mogul.api.podcasts.Segment}, this might actually be the title
	 * of the {@link com.joshlong.mogul.api.podcasts.Episode episode} to which it belongs.
	 */

	String title(Long searchableId);

	/**
	 * returns the thing that people might be interested in looking at. e.g.: the
	 * {@link Searchable } might point to the
	 * {@link com.joshlong.mogul.api.podcasts.Segment segment}, but the user would want
	 * the {@link com.joshlong.mogul.api.podcasts.Episode episode}.
	 */
	AGGREGATE aggregate(Long searchableId);

}
