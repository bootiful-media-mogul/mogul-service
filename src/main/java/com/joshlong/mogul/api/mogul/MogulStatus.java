package com.joshlong.mogul.api.mogul;

import com.joshlong.mogul.api.Publishable;

import java.time.LocalDate;
import java.util.Date;

/**
 * a single day in the life of a {@link Mogul}, and the thing you actually publish when
 * you publish "about" a mogul: today's status goes out to social media, tomorrow's is a
 * fresh sheet.
 * <p>
 * this is the indirection that keeps the {@link Publishable} contract honest. a
 * {@code Publishable} is an artifact with finite content that gets produced, socialized a
 * handful of times, and then left behind for the next one. a mogul is none of those
 * things. a mogul's <em>day</em> is all of them.
 * <p>
 * there is at most one of these per mogul per date, and rows only exist for days on which
 * something happened.
 */
public record MogulStatus(Long id, Long mogulId, LocalDate date, Date created) implements Publishable {

	@Override
	public Long publishableId() {
		return this.id;
	}
}
