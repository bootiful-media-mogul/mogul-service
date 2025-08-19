package com.joshlong.mogul.api.search.index;

public record DocumentChunk(long id, String text, /* double score, */ Long documentId) {

}
