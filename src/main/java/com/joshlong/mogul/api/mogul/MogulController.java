package com.joshlong.mogul.api.mogul;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;

import java.security.Principal;
import java.util.Map;

@Controller
class MogulController {

	@QueryMapping
	Map<String, String> me(Principal principal) {
		return Map.of("name", principal.getName());
	}

}
