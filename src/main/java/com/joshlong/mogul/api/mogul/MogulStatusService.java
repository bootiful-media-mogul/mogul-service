package com.joshlong.mogul.api.mogul;

import java.time.LocalDate;
import java.util.Collection;

public interface MogulStatusService {

	/**
	 * the mogul's status for the current day, created on first use. this is the entry
	 * point for anything that wants to attach something to "now".
	 */
	MogulStatus today(Long mogulId);

	MogulStatus getMogulStatusById(Long id);

	/**
	 * @return the status for that date, or {@literal null} if nothing happened that day
	 */
	MogulStatus getMogulStatusByDate(Long mogulId, LocalDate date);

	/**
	 * the most recent statuses, newest first. these are the days the mogul actually did
	 * something, not the last {@code limit} calendar days.
	 */
	Collection<MogulStatus> getRecentMogulStatuses(Long mogulId, int limit);

}
