package com.joshlong.mogul.api.publications;

import com.joshlong.mogul.api.Publication;
import com.joshlong.mogul.api.mogul.MogulStatus;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * resolves the publications hanging off a {@link MogulStatus}.
 * <p>
 * this lives here, and not in the mogul module, because publications know about the
 * things they publish and not the other way around. letting the mogul module reach back
 * into {@link PublicationService} would put a cycle between the two.
 */
@Controller
class MogulStatusPublicationsController {

	private final PublicationService publicationService;

	MogulStatusPublicationsController(PublicationService publicationService) {
		this.publicationService = publicationService;
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

}
