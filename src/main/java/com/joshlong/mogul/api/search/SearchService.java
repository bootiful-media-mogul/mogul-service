package com.joshlong.mogul.api.search;

import com.joshlong.mogul.api.Searchable;
import com.joshlong.mogul.api.search.index.IndexService;
import com.joshlong.mogul.api.search.index.SearchHit;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * implementors of the {@link Searchable searchable} type are expected to call the
 * {@link SearchService#index(Searchable)} method whenever the indexable text and metadata
 * may have reasonably changed.
 * <p>
 * TODO: should there be some sort of mapping between the {@link Searchable } and the
 * underlying implementation? After all, given the {@link Searchable#searchableId()}, how
 * do we load and resolve the source entity?
 */
public interface SearchService {

	<T extends Searchable> void index(T searchable);

	Collection<? extends Searchable> search(String query, Map<String, Object> metadata);

}

@Component
class SearchRunner implements ApplicationRunner {

	private final SearchService searchService;

	SearchRunner(SearchService searchService) {
		this.searchService = searchService;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		var all = this.searchService.search("Spring Boot", Map.of());
		for (var searchable : all) {
			System.out.println("searchable #" + searchable);
		}
	}

}

@Service
class DefaultSearchService implements SearchService {

	private static final String KEY = "key";

	private static final String CLASS = "class";

	private final Map<String, SearchableRepository<?, ?>> repositories = new ConcurrentHashMap<>();

	private final IndexService index;

	DefaultSearchService(Map<String, SearchableRepository<?, ?>> repositories, IndexService index) {
		this.index = index;
		this.repositories.putAll(repositories);
	}

	private SearchableRepository<?, ?> repositoryFor(Class<?> clzz) {
		for (var sr : this.repositories.values())
			if (sr.supports(clzz))
				return sr;
		return null;
	}

	@Override
	public <T extends Searchable> void index(T searchable) {
		var searchableId = searchable.searchableId();
		var clzz = keyFor(searchable.getClass());
		var repo = repositoryFor(searchable.getClass());
		Assert.notNull(repo, () -> "there's no repository for " + clzz + "!");
		var textForSearchable = repo.text(searchableId);
		var titleForSearchable = repo.title(searchableId);
		this.index.ingest(titleForSearchable, textForSearchable, Map.of(KEY, searchableId, CLASS, clzz));
	}

	private String keyFor(Class<?> clzz) {
		return clzz != null ? clzz.getName() : null;
	}

	@Override
	public Collection<? extends Searchable> search(String query, Map<String, Object> metadata) {
		var results = new LinkedHashSet<Searchable>();
		try {
			var all = this.index.search(query, metadata);
			all.sort(Comparator.comparing(SearchHit::score));

			for (var hit : all) {
				var documentChunk = hit.documentChunk();
				var documentId = documentChunk.documentId();
				var document = index.documentById(documentId);
				var resultMetadata = document.metadata();
				Assert.notNull(resultMetadata, () -> "no metadata found for document id " + documentId);
				var clzz = (String) (resultMetadata.getOrDefault(CLASS, null));
				var searchableId = ((Number) (resultMetadata.getOrDefault(KEY, null))).longValue();
				var clzzObj = Class.forName(clzz);
				var repo = this.repositoryFor(clzzObj);
				var result = repo.find(searchableId);
				Assert.notNull(result, () -> "could not find " + clzz + " with id " + searchableId);
				results.add(result);
			}
		} //
		catch (Throwable throwable) {
			throw new RuntimeException(throwable);
		}
		return results;
	}

}