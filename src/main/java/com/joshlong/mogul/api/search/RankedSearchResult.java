package com.joshlong.mogul.api.search;

import java.util.Date;

public record RankedSearchResult(long searchableId, long aggregateId, String title, String description, String type,
								 double rank, Date created) {
}
