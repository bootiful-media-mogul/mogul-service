package com.joshlong.mogul.api.notifications;

import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.notifications.ably.AblyTokenService;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
class AblyController {

	private final AblyTokenService tokenService;

	private final MogulService mogulService;

	AblyController(AblyTokenService tokenService, MogulService mogulService) {
		this.tokenService = tokenService;
		this.mogulService = mogulService;
	}

	@QueryMapping
	NotificationContext notificationContext() throws Exception {
		var topicName = AblyNotificationsUtils.ablyNoticationsChannelFor(this.mogulService.getCurrentMogul().id());
		var ttl = 60 * 60 * 1000L; // 1 hour
		var tokenRequest = this.tokenService.createTokenFor(topicName, ttl);
		var tr = new TokenRequest(tokenRequest.keyName, tokenRequest.nonce, tokenRequest.mac,
				Long.toString(tokenRequest.timestamp), Long.toString(tokenRequest.ttl), tokenRequest.capability);
		return new NotificationContext(topicName, tr);
	}

}

record TokenRequest(String keyName, String nonce, String mac, String timestamp, String ttl, String capability) {
}

record NotificationContext(String ablyChannel, TokenRequest ablyTokenRequest) {
}
