package com.joshlong.mogul.api.ayrshare;

import com.joshlong.mogul.api.mogul.MogulService;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.*;

@Controller
class AyrshareController {

	private final AyrshareService ayrshareService;

	private final MogulService mogulService;

	AyrshareController(AyrshareService ayrshareService, MogulService mogulService) {
		this.ayrshareService = ayrshareService;
		this.mogulService = mogulService;
	}

	@SchemaMapping
	String platform(AyrsharePublicationComposition ayrsharePublicationComposition) {
		return ayrsharePublicationComposition.platform().platformCode();
	}

	@QueryMapping
	Collection<AyrsharePublicationComposition> ayrsharePublicationCompositions() {
		var mogulId = this.mogulService.getCurrentMogul().id();
		return this.ayrshareService.getDraftAyrsharePublicationCompositionsFor(mogulId);
	}

	@QueryMapping
	Set<String> ayrsharePlatforms() {
		var ps = Arrays.stream(this.ayrshareService.platforms()) //
			.sorted(Comparator.comparing(Platform::platformCode)) //
			.toArray(Platform[]::new);
		var unique = new LinkedHashSet<String>(ps.length);
		for (var p : ps)
			unique.add(p.platformCode());
		return unique;
	}

}
