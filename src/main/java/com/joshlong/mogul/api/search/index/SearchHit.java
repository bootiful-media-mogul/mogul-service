package com.joshlong.mogul.api.search.index;

public record SearchHit(DocumentChunk documentChunk, double score) {
}
