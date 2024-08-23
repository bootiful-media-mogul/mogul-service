package com.joshlong.mogul.api.mogul;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@Controller
class MogulController {

	@QueryMapping
	Map<String, String> me(Principal principal) {
		var map = new HashMap<String, String>();
		map.put("name", principal.getName());
		if (principal instanceof DefaultMogulService.MogulJwtAuthenticationToken mogulJwtAuthenticationToken) {
			map.put("email", mogulJwtAuthenticationToken.details().email());
			map.put("givenName", mogulJwtAuthenticationToken.details().givenName());
			map.put("familyName", mogulJwtAuthenticationToken.details().familyName());
		}
		return map;
	}

}
