package com.joshlong.mogul.api.search;

public record RankedResult(long searchableId, String title, String description, String type, double rank) {
}
