package com.joshlong.mogul.api.search;

public record SearchHit(DocumentChunk documentChunk, double score) {
}
