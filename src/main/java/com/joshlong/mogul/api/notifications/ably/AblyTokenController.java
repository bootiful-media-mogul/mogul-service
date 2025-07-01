package com.joshlong.mogul.api.notifications.ably;

import com.joshlong.mogul.api.ApiProperties;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.rest.Auth;
import io.ably.lib.types.AblyException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * todo should this be a graphql controller using the function callback approach?
 */
@Controller
@ResponseBody
class AblyTokenController {

	private final AblyRealtime ablyRealtime;

	private final String apiKey;

	AblyTokenController(AblyRealtime ablyRealtime, ApiProperties properties) {
		this.ablyRealtime = ablyRealtime;
		this.apiKey = properties.notifications().ably().apiKey();
	}

	@GetMapping("/notifications/ably/token")
	ResponseEntity<String> getTokenRequest() throws AblyException {
		var tokenParams = new Auth.TokenParams();
		// Optionally restrict capabilities (e.g., subscribe-only to certain channels)
		// tokenParams.capability = "{\"my-topic\":[\"subscribe\"]}";
		tokenParams.ttl = 60 * 60 * 1000L; // 1 hour

		var authOptions = new Auth.AuthOptions();
		authOptions.key = apiKey;
		var tokenRequest = ablyRealtime.auth.createTokenRequest(tokenParams, authOptions);

		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(tokenRequest.asJson());

	}

}
