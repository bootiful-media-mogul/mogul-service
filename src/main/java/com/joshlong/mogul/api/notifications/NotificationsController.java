package com.joshlong.mogul.api.notifications;

import com.joshlong.mogul.api.mogul.MogulService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Controller
@RegisterReflectionForBinding(NotificationEvent.class)
class NotificationsController {

	private final Map<Long, Queue<NotificationEvent>> events = new ConcurrentHashMap<>();

	private final MogulService mogulService;

	private final Logger log = LoggerFactory.getLogger(NotificationsController.class);

	NotificationsController(MogulService mogulService) {
		this.mogulService = mogulService;
	}

	@ApplicationModuleListener
	void notificationEventListener(NotificationEvent notification) {
		Assert.notNull(notification, "the notification must not be null");
		var mogulId = notification.mogulId();
		this.events.computeIfAbsent(mogulId, aLong -> new ConcurrentLinkedQueue<>()).add(notification);
		this.log.debug("adding notification event {}. this is the collection: {}", notification,
				this.events.get(mogulId));

	}

	@QueryMapping
	Map<String, Object> notifications() {
		var currentMogul = this.mogulService.getCurrentMogul();
		if (currentMogul == null) {
			return new HashMap<>();
		}
		var currentMogulId = currentMogul.id();
		var notificationEvents = this.events.computeIfAbsent(currentMogulId, mogulId -> new ConcurrentLinkedQueue<>());
		var notification = notificationEvents.poll();
		if (null != notification) {
			Assert.notNull(currentMogulId, "the current mogul id should not be null");
			Assert.state(notification.when().getTime() > 0, "the time must not be null");
			Assert.state(StringUtils.hasText(notification.key()), "the key must not be null");
			Assert.state(StringUtils.hasText(notification.category()), "the category must not be null");
			var m = new HashMap<String, Object>(Map.of("mogulId", currentMogulId, //
					"when", notification.when().getTime(), //
					"key", notification.key(), "category", notification.category(), "modal", notification.modal()));
			if (StringUtils.hasText(notification.context()))
				m.put("context", notification.context());
			return m;
		}
		return null;
	}

	@MutationMapping
	boolean notify(@Argument boolean visible, @Argument boolean modal) {
		var object = new TestEvent("sent a " + (modal ? "modal" : "") + (visible ? "" : ", and visible") + " message");
		var id = mogulService.getCurrentMogul().id();
		var notificationEvent = NotificationEvent.notificationEventFor(id, object, id.toString(), null, modal, visible);
		NotificationEvents.notify(notificationEvent);
		this.log.debug("sent notification event {}", notificationEvent);
		return true;
	}

}

record TestEvent(String message) {
}