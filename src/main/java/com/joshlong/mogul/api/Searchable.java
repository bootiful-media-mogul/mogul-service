package com.joshlong.mogul.api;

import java.util.Map;

/**
 * represents a thing that's been indexed for searching.
 */
public interface Searchable {
    Long searchableId();
    String text();
    Map<String,String> metadata();
}
