package com.joshlong.mogul.api.ayrshare;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.LinkedHashSet;
import java.util.Set;

@Controller
class AyrshareController {

	private final Ayrshare client;

	AyrshareController(Ayrshare client) {
		this.client = client;
	}

	@QueryMapping
	Set<String> ayrsharePlatforms() {
		var ps = this.client.platforms();
		var unique = new LinkedHashSet<String>(ps.length);
		for (var p : ps)
			unique.add(p.platformCode());
		return unique;
	}

}
