package com.joshlong.mogul.api.mogul;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@Controller
class MogulController {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final MogulService mogulService;

	MogulController(MogulService mogulService) {
		this.mogulService = mogulService;
	}

	@QueryMapping
	Map<String, String> me(Principal principal) {
		var mogulByName = mogulService.getMogulByName(principal.getName());
		var map = new HashMap<String, String>();
		map.put("name", mogulByName.username());
		map.put("email", mogulByName.email());
		map.put("givenName", mogulByName.givenName());
		map.put("familyName", mogulByName.familyName());
		return map;
	}

}
