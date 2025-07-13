package com.joshlong.mogul.api.ayrshare;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.Arrays;
import java.util.Comparator;
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
		var ps = Arrays.stream(this.client.platforms()) //
			.sorted(Comparator.comparing(Ayrshare.Platform::platformCode)) //
			.toArray(Ayrshare.Platform[]::new);
		var unique = new LinkedHashSet<String>(ps.length);
		for (var p : ps)
			unique.add(p.platformCode());
		return unique;
	}

}
