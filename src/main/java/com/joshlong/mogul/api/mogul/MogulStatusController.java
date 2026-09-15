package com.joshlong.mogul.api.mogul;

import com.joshlong.mogul.api.Publication;
import com.joshlong.mogul.api.publications.PublicationService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
class MogulStatusController {

	private static final int DEFAULT_RECENT_LIMIT = 10;

	private final MogulService mogulService;

	private final MogulStatusService mogulStatusService;

	private final PublicationService publicationService;

	MogulStatusController(MogulService mogulService, MogulStatusService mogulStatusService,
			PublicationService publicationService) {
		this.mogulService = mogulService;
		this.mogulStatusService = mogulStatusService;
		this.publicationService = publicationService;
	}

	@QueryMapping
	MogulStatus mogulStatusToday() {
		return this.mogulStatusService.today(this.mogulService.getCurrentMogul().id());
	}

	@QueryMapping
	Collection<MogulStatus> mogulStatuses(@Argument Integer limit) {
		var mogulId = this.mogulService.getCurrentMogul().id();
		return this.mogulStatusService.getRecentMogulStatuses(mogulId,
				null == limit || limit <= 0 ? DEFAULT_RECENT_LIMIT : limit);
	}

	/**
	 * batched on purpose: asking for ten days of publications costs one trip through
	 * {@link PublicationService}, and the publication row mapper in turn reads all of
	 * their outcomes in one more. rendering N days is not N times the work.
	 */
	@BatchMapping
	Map<MogulStatus, List<Publication>> publications(List<MogulStatus> mogulStatuses) {
		var ids = mogulStatuses.stream().map(MogulStatus::id).collect(Collectors.toSet());
		var publicationsByStatusId = this.publicationService.getPublicationsByPublicationKeysAndClass(ids,
				MogulStatus.class);
		var map = new LinkedHashMap<MogulStatus, List<Publication>>();
		for (var mogulStatus : mogulStatuses)
			map.put(mogulStatus, new ArrayList<>(publicationsByStatusId.getOrDefault(mogulStatus.id(), List.of())));
		return map;
	}

	@SchemaMapping
	String date(MogulStatus mogulStatus) {
		return mogulStatus.date().toString();
	}

}
