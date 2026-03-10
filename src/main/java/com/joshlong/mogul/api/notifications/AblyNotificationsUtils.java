package com.joshlong.mogul.api.notifications;

/**
 * @author Josh Long
 */
public abstract class AblyNotificationsUtils {

	/**
	 * the idea is that the browser client could listen for notifications intended for
	 * just that client. we'll also make sure to limit access to that topic when we issue
	 * the short-term token
	 */
	public static String ablyNotificationsChannelFor(Long mogulId) {
		return "notifications-" + mogulId;
	}


}
