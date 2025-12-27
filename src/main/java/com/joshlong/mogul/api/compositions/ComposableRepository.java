package com.joshlong.mogul.api.compositions;

import com.joshlong.mogul.api.DomainRepository;

/**
 * Repository interface for entities that can have compositions.
 *
 * Implementations provide the strategy for loading entity instances that implement the
 * Composable marker interface.
 *
 * @param <T> The concrete entity type that implements Composable
 */
public interface ComposableRepository<T extends Composable> extends DomainRepository<Composable, T> {

}
