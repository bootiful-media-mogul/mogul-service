package com.joshlong.mogul.api.notifications;

import com.joshlong.mogul.api.mogul.MogulService;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.support.TransactionTemplate;

@Controller
@RegisterReflectionForBinding(NotificationEvent.class)
class NotificationsController {

	private final MogulService mogulService;

	private final TransactionTemplate transactionTemplate;

	NotificationsController(MogulService mogulService, TransactionTemplate transactionTemplate) {
		this.mogulService = mogulService;
		this.transactionTemplate = transactionTemplate;
	}

	// todo remove this once we've integrated Ably!
	@MutationMapping
	@SuppressWarnings("ConstantConditions")
	boolean notify(@Argument boolean visible, @Argument boolean modal) {
		var object = new TestEvent("sent a " + (modal ? "modal" : "") + (visible ? "" : ", and visible") + " message");
		var id = this.mogulService.getCurrentMogul().id();
		return this.transactionTemplate.execute(_ -> {
			var notificationEvent = NotificationEvent.notificationEventFor(id, object, id.toString(), null, modal,
					visible);
			NotificationEvents.notify(notificationEvent);
			return true;
		});

	}

	// todo i dont think we'll need this endpoint nor the client side code that queries it
	// since we have Ably now. Ably can give you history, too. So it'd make more sense to
	// just
	// query Ably directly from the client, i guess.
	//

	/*
	 * this endpoint takes the latest notification and returns it, or an empty map if
	 * there's nothing newer.
	 */
	/*
	 * @QueryMapping Map<String, Object> notifications() { var currentMogul =
	 * this.mogulService.getCurrentMogul(); if (currentMogul == null) { return new
	 * HashMap<>(); } var currentMogulId = currentMogul.id(); var notificationEvents =
	 * this.events.computeIfAbsent(currentMogulId, mogulId -> new
	 * ConcurrentLinkedQueue<>()); var notification = notificationEvents.poll(); if (null
	 * != notification) { Assert.notNull(currentMogulId,
	 * "the current mogul id should not be null");
	 * Assert.state(notification.when().getTime() > 0, "the time must not be null");
	 * Assert.state(StringUtils.hasText(notification.key()), "the key must not be null");
	 * Assert.state(StringUtils.hasText(notification.category()),
	 * "the category must not be null"); var m = new HashMap<String,
	 * Object>(Map.of("mogulId", currentMogulId, // "when", notification.when().getTime(),
	 * // "key", notification.key(), "category", notification.category(), "modal",
	 * notification.modal())); if (StringUtils.hasText(notification.context()))
	 * m.put("context", notification.context()); return m; } return null; }
	 */
	// todo remove this since its just for testing

}

// todo remove this since its just for testing

record TestEvent(String message) {
}