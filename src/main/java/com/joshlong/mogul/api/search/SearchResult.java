package com.joshlong.mogul.api.search;

public record SearchResult(long searchableId, String title, String description, String type, double rank) {
}
