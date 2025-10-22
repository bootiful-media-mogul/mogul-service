package com.joshlong.mogul.api.search;

import com.joshlong.mogul.api.Searchable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
class DefaultSearchService implements SearchService {

	private final Logger log = LoggerFactory.getLogger(DefaultSearchService.class);

	private static final String KEY = "key";

	private static final String CLASS = "class";

	private final Map<String, SearchableRepository<?, ?>> repositories = new ConcurrentHashMap<>();

	private final Index index;

	DefaultSearchService(Map<String, SearchableRepository<?, ?>> repositories, Index index) {
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
		if (StringUtils.hasText(textForSearchable) && StringUtils.hasText(titleForSearchable)) {
			this.index.ingest(titleForSearchable, textForSearchable, Map.of(KEY, searchableId, CLASS, clzz));
		} //
		else {
			this.log.debug("we've got nothing to index for searchable {} with class {}!", searchableId, clzz);
		}
	}

	@Override
	public Collection<? extends Searchable> search(String query, Map<String, Object> metadata) {
		var results = new LinkedHashSet<Searchable>();
		try {
			var all = this.index.search(query, metadata);
			all.sort(Comparator.comparing(IndexHit::score));
			for (var hit : all) {
				var documentChunk = hit.documentChunk();
				var documentId = documentChunk.documentId();
				var document = this.index.documentById(documentId);
				var resultMetadata = document.metadata();
				Assert.notNull(resultMetadata, () -> "no metadata found for document id " + documentId);
				var clzz = (String) (resultMetadata.getOrDefault(CLASS, null));
				var searchableId = ((Number) (resultMetadata.getOrDefault(KEY, null))).longValue();
				var clzzObj = Class.forName(clzz);
				var repo = Objects.requireNonNull(this.repositoryFor(clzzObj),
						"there is no repository for " + clzz + ".");
				var result = Objects.requireNonNull(repo.find(searchableId),
						"could not find " + clzz + " with id " + searchableId + '.');
				results.add(result);
			}
		} //
		catch (Throwable throwable) {
			throw new RuntimeException(throwable);
		}
		return results;
	}

	private String keyFor(Class<?> clzz) {
		return clzz != null ? clzz.getName() : null;
	}

}
