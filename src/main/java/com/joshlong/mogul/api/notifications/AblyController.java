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

@Controller
@ResponseBody
class AblyController {

	private final AblyTokenService tokenService;

	private final MogulService mogulService;

	AblyController(AblyTokenService tokenService, MogulService mogulService) {
		this.tokenService = tokenService;
		this.mogulService = mogulService;
	}

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
