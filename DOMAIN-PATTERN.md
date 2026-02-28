# Domain Pattern Convention

This document describes the **Domain Pattern**, a convention used throughout the Mogul codebase for implementing
cross-cutting concerns that can be applied to multiple unrelated entity types.

## Overview

The Domain Pattern allows different entity types (Episodes, Posts, Segments, etc.) to participate in cross-cutting
concerns (publications, transcriptions, compositions, etc.) without tight coupling or inheritance hierarchies.

### Key Concepts

- **Domain**: A cross-cutting concern like "publications", "transcriptions", or "compositions"
- **Marker Interface**: An interface (e.g., `Publishable`, `Transcribable`) that marks an entity as participating in a
  domain
- **Domain Type**: A record (e.g., `Publication`, `Transcript`) that stores the domain-specific data
- **Repository**: A strategy for loading entity instances that implement the marker interface
- **Service**: Orchestrates the lifecycle of domain types using repositories

## Architecture

### 1. Marker Interface

Defines entities that can participate in a domain concern.

```java
public interface Publishable {
    Long publishableId();  // Returns the entity's unique identifier
}
```

**Naming Convention**: `{Domain}able` (e.g., `Publishable`, `Transcribable`, `Composable`, `Notable`)

### 2. Domain Type (Record)

Stores the domain-specific data with a reference to the owning entity.

```java
public record Publication(
    Long mogulId,
    Long id,
    String plugin,
    Date created,
    Date published,
    Map<String, String> context,
    String payload,           // Serialized entity ID
    Class<?> payloadClass,    // Entity class name
    State state,
    List<Outcome> outcomes
) {}
```

**Key Fields**:

- `payload`: JSON-serialized ID of the owning entity
- `payloadClass`: Fully qualified class name of the owning entity (e.g., `Episode.class`)

### 3. Repository Interface

Defines the strategy for loading entity instances.

```java
public interface PublishableRepository<T extends Publishable>
        extends DomainRepository<Publishable, T> {
    // Pattern-specific methods can be added here
}
```

**Base Interface** (`DomainRepository`):

- `boolean supports(Class<?> clazz)` - Checks if this repository handles the given entity class
- `T find(Long key)` - Loads an entity instance by its ID

### 4. Repository Implementation

Implements the strategy for a specific entity type.

```java
@Component
class PodcastPublishableRepository extends AbstractPublishableRepository<Episode> {

    private final PodcastService podcastService;

    PodcastPublishableRepository(PodcastService podcastService) {
        super(Episode.class);  // Declares this repository handles Episode
        this.podcastService = podcastService;
    }

    @Override
    public Episode find(Long id) {
        return podcastService.getPodcastEpisodeById(id);
    }
}
```

**Abstract Base Class** (`AbstractDomainRepository`):

- Automatically implements `supports()` based on the entity class
- Reduces boilerplate in concrete implementations

### 5. Service

Orchestrates domain operations using repositories.

```java
class DefaultPublicationService
        extends AbstractDomainService<Publishable, PublishableRepository<?>>
        implements PublicationService {

    DefaultPublicationService(Collection<PublishableRepository<?>> repositories) {
        super(repositories);  // Base class manages repository resolution
    }

    public <T extends Publishable> T resolvePublishable(Long id, Class<T> clazz) {
        return findEntity(clazz, id);  // Uses base class helper
    }
}
```

**Abstract Base Class** (`AbstractDomainService`):

- `findRepository(Class)` - Finds the appropriate repository at runtime
- `findEntity(Class, Long)` - Finds and loads an entity using the right repository

## Implementing a New Domain

### Step 1: Create the Marker Interface

```java
public interface Notable {
    Long notableId();
}
```

### Step 2: Create the Domain Type

```java
public record Note(
    Long mogulId,
    Long id,
    Date created,
    String url,
    String note,
    String payload,
    Class<?> payloadClass
) {}
```

### Step 3: Create the Repository Interface

```java
public interface NotableRepository<T extends Notable>
        extends DomainRepository<Notable, T> {
    // Add pattern-specific methods if needed
}
```

### Step 4: Create Abstract Repository Base (Optional)

```java
public abstract class AbstractNotableRepository<T extends Notable>
        extends AbstractDomainRepository<Notable, T>
        implements NotableRepository<T> {

    protected AbstractNotableRepository(Class<T> entityClass) {
        super(entityClass);
    }
}
```

### Step 5: Implement Repositories for Entity Types

```java
@Component
class EpisodeNotableRepository extends AbstractNotableRepository<Episode> {

    private final PodcastService podcastService;

    EpisodeNotableRepository(PodcastService podcastService) {
        super(Episode.class);
        this.podcastService = podcastService;
    }

    @Override
    public Episode find(Long id) {
        return podcastService.getPodcastEpisodeById(id);
    }
}
```

### Step 6: Implement the Service

```java
@Service
class NoteService extends AbstractDomainService<Notable, NotableRepository<?>> {

    NoteService(Collection<NotableRepository<?>> repositories) {
        super(repositories);
    }

    public <T extends Notable> Note createNote(Long id, Class<T> clazz, String noteText) {
        T entity = findEntity(clazz, id);
        // Create and persist note...
    }
}
```

### Step 7: Mark Entity Types as Notable

```java
public record Episode(
    Long id,
    String title,
    // ... other fields
) implements Publishable, Composable, Notable {  // Add Notable

    @Override
    public Long notableId() {
        return id;
    }
}
```

## Existing Domains

### Publications (`Publishable`)

**Purpose**: Publishing entities to various platforms (blogs, social media, etc.)

**Entities**: `Episode`, `Post`

**Repository**: `PublishableRepository<T>`

**Service**: `PublicationService`

---

### Transcriptions (`Transcribable`)

**Purpose**: Audio transcription for entities with audio files

**Entities**: `Segment`

**Repository**: `TranscribableRepository<T>`

- Additional method: `Resource audio(Long key)` - Returns the audio resource

**Service**: `TranscriptService`

---

### Compositions (`Composable`)

**Purpose**: Textual content with managed file attachments

**Entities**: `Podcast`, `Episode`, `AyrsharePublicationComposition`

**Repository**: `ComposableRepository<T>` *(repository implementations needed)*

**Service**: `CompositionService`

---

### Search (`Searchable`)

**Purpose**: Indexing and searching entities

**Entities**: `Segment`

**Repository**: `SearchableRepository<T, AGGREGATE>`

- Additional generic: `AGGREGATE` - The aggregation type (e.g., `Episode` for `Segment`)
- Additional method: `SearchableResult<T, AGGREGATE> result(T searchable)`

**Service**: `SearchService`

---

### Notes (`Notable`)

**Purpose**: Attaching notes/annotations to entities

**Entities**: *(implementations needed)*

**Repository**: *(not yet implemented)*

**Service**: `NoteService` *(stub implementation)*

## Benefits

1. **Type Safety**: Generics ensure compile-time type checking
2. **Convention over Configuration**: Clear, consistent pattern across all domains
3. **Reduced Boilerplate**: Abstract base classes eliminate repetitive code
4. **Loose Coupling**: Entities don't need to know about domain implementations
5. **Extensibility**: Easy to add new domains or new entity types to existing domains
6. **Runtime Polymorphism**: Services automatically resolve the correct repository
7. **Spring Integration**: Works naturally with dependency injection

## Database Schema Pattern

Domain tables follow a consistent schema:

```sql
create table domain_name (
    id serial primary key,
    mogul_id bigint not null references mogul(id),
    created timestamp not null default now(),
    -- domain-specific fields
    payload text not null,          -- JSON serialized entity ID
    payload_class text not null,    -- Fully qualified class name
    unique (payload_class, payload, ...)  -- Prevent duplicates
);
```

Example:

```sql
create table publication (
    id serial primary key,
    mogul_id bigint not null references mogul(id),
    plugin text not null,
    created timestamp not null default now(),
    published timestamp null,
    context text not null,
    payload text not null,
    payload_class text not null,
    state text not null
);
```

## Best Practices

1. **Use Abstract Base Classes**: Extend `AbstractDomainRepository` and `AbstractDomainService` to reduce boilerplate
2. **Pattern-Specific Methods**: Add domain-specific methods to repository interfaces (e.g., `audio()` for
   `TranscribableRepository`)
3. **Consistent Naming**: Follow the `-able` suffix convention for marker interfaces
4. **Single Responsibility**: Keep repositories focused on entity loading
5. **Service Layer**: Put business logic in services, not repositories
6. **Spring Components**: Mark repositories with `@Component` for auto-discovery
7. **Type Safety**: Use generics consistently throughout the pattern

## Testing

When testing components using the Domain Pattern:

```java
@Test
void testPublishableResolution() {
    var episode = new Episode(1L, "Test Episode", ...);
    var repository = new PodcastPublishableRepository(podcastService);

    assertTrue(repository.supports(Episode.class));
    assertFalse(repository.supports(Post.class));

    var resolved = repository.find(1L);
    assertEquals(episode, resolved);
}
```

## Migration Guide

To migrate existing code to use the Domain Pattern:

1. Update repository interfaces to extend `DomainRepository`
2. Create abstract repository base classes extending `AbstractDomainRepository`
3. Update repository implementations to extend the abstract base
4. Update service classes to extend `AbstractDomainService`
5. Replace manual repository iteration with `findEntity()` or `findRepository()` calls

See the Publications domain for a complete example of the pattern in action.
