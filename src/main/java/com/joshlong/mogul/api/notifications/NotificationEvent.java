package com.joshlong.mogul.api.notifications;

import org.springframework.util.Assert;

import java.util.Date;

/**
 * describes an event - any event - that should be communicated to a particular,
 * logged-in, user.
 *
 * @param visible should the notification be shown at all, or is it meant for a client
 * side update that might get rendered in some other way?
 * @param modal does this notification require user acknowledgment, or can it sort of
 * appear briefly and then fade away?
 * @param when when the message was published. <EM>Not</EM> when (or if) the message has
 * been routed, delivered, or read.
 * @param key some extra information that, when paired with the category, tells us a thing
 * and tell us to which other thing the message applies; e.g.: a podcast-episode and an
 * ID.
 * @param category a machine-friendly string that is constant, indicating the nature of
 * the notification, e.g.: <code>podcast-episode-completed</code>. It is expected that the
 * client would use this category to lookup internationalized messages for each
 * notification
 * @param context any extra context associated with the notification. <code>null</code> or
 * <code>""</coded> if unneeded.
 *                 if this is a system-wide message that needs to be delivered, for which there is
 *                 no internationalized text, then this could be a human-readable message sent in whatever language is required.
 *
@param mogulId  the current authenticated user for whom this context is intended or <code>NULL</code>
 * if this is meant to be a system-wide notification intended for all users logged into
 * the system
 */
public record NotificationEvent(Long mogulId, String category, String key, Date when, String context, boolean modal,
		boolean visible) {

	public static NotificationEvent systemNotificationEventFor(Long mogulId, Object object, String key,
			String context) {
		return notificationEventFor(mogulId, object, key, context, false, false);
	}

	public static NotificationEvent visibleNotificationEventFor(Long mogulId, Object object, String key,
			String context) {
		return notificationEventFor(mogulId, object, key, context, false, true);
	}

	public static NotificationEvent modalNotificationEventFor(Long mogulId, Object object, String key, String context) {
		return notificationEventFor(mogulId, object, key, context, true, false);
	}

	private static NotificationEvent notificationEventFor(Long mogulId, Object object, String key, String context,
			boolean modal, boolean visible) {
		Assert.notNull(object, "object cannot be null");
		Assert.notNull(mogulId, "mogulId cannot be null");
		var cat = categoryFromClassName(object.getClass());
		return new NotificationEvent(mogulId, cat, key, new Date(), context, modal, visible);
	}

	private static String categoryFromClassName(Class<?> clazz) {
		Assert.notNull(clazz, "the class must be non-null");
		var clazzName = clazz.getSimpleName();
		Assert.hasText(clazzName, "the class name must have text");
		var buffer = new StringBuilder();
		for (var c : clazzName.toCharArray()) {
			if (Character.isUpperCase(c)) {
				buffer.append("-").append(Character.toLowerCase(c));
			} //
			else {
				buffer.append(c);
			}
		}
		var tos = buffer.toString();
		if (tos.startsWith("-"))
			tos = tos.substring(1);
		return tos;
	}
}
