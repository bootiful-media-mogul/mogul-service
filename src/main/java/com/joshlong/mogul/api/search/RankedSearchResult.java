package com.joshlong.mogul.api.search;

public record RankedSearchResult(long searchableId, long aggregateId, String title, String description, String type,
		double rank) {
}
