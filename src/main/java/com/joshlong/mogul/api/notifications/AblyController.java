package com.joshlong.mogul.api.notifications;

import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.notifications.ably.AblyTokenService;
import io.ably.lib.types.AblyException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * todo should this be a graphql controller using the function callback approach? Ably
 * supports <EM>either</EM> a URL (that it calls? or?) <EM>or</EM> a callback in which you
 * are given parameters, do something, and arrive at the token on your own. I think making
 * this a graphql controller is more logical. but we need to get it working one way or
 * another.
 */
@Controller
@ResponseBody
class AblyController {

	private final AblyTokenService tokenService;

	private final MogulService mogulService;

	AblyController(AblyTokenService tokenService, MogulService mogulService) {
		this.tokenService = tokenService;
		this.mogulService = mogulService;
	}

	// todo i think we need maybe two endpoints.
	// one to return the token data
	// another to return the name of the topic to which the client should listen.
	// but for now lets hardcode it.

	@GetMapping("/notifications/ably/token")
	ResponseEntity<String> getTokenRequest() throws AblyException {
		var topicName = AblyNotificationsUtils.ablyNoticationsChannelFor(this.mogulService.getCurrentMogul().id());
		var ttl = 60 * 60 * 1000L; // 1 hour
		var tokenRequest = this.tokenService.createTokenFor(topicName, ttl);
		return ResponseEntity.ok() //
			.contentType(MediaType.APPLICATION_JSON) //
			.body(tokenRequest.asJson());

	}

	@GetMapping("/notifications/ably/channel")
	Map<String, String> channelName() {
		var mogulId = this.mogulService.getCurrentMogul().id();
		return Map.of("channel", AblyNotificationsUtils.ablyNoticationsChannelFor(mogulId));
	}

}
