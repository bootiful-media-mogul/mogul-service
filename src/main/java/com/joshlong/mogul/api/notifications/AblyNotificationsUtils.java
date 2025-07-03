package com.joshlong.mogul.api.notifications;

import com.joshlong.mogul.api.mogul.Mogul;

/**
 * @author Josh Long
 */
public abstract class AblyNotificationsUtils {

	/**
	 * the idea is that the browser client could listen for notifications intended for
	 * just that client. we'll also make sure to limit access to that topic when we issue
	 * the short-term token
	 */
	public static String ablyNoticationsChannelFor(Long mogulId) {
		return "notifications-" + mogulId;
	}

	public static String ablyNoticationsChannelFor(Mogul mogul) {
		return ablyNoticationsChannelFor(mogul.id());
	}

}
