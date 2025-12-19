package com.joshlong.mogul.api.search;

public record SearchableResultAggregate<T>(Long id, T result) {
}
