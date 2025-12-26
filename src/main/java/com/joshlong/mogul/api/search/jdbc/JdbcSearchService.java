package com.joshlong.mogul.api.search.jdbc;

import com.joshlong.mogul.api.Searchable;
import com.joshlong.mogul.api.Transcribable;
import com.joshlong.mogul.api.search.RankedSearchResult;
import com.joshlong.mogul.api.search.SearchService;
import com.joshlong.mogul.api.search.SearchableRepository;
import com.joshlong.mogul.api.transcripts.TranscriptRecordedEvent;
import com.joshlong.mogul.api.utils.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Transactional
class JdbcSearchService implements SearchService {

	private static final String KEY = "key";

	private static final String CLASS = "class";

	private final Logger log = LoggerFactory.getLogger(JdbcSearchService.class);

	private final Map<String, SearchableRepository<?, ?>> repositories = new ConcurrentHashMap<>();

	private final Index index;

	JdbcSearchService(Map<String, SearchableRepository<?, ?>> repositories, Index index) {
		this.index = index;
		this.repositories.putAll(repositories);
	}

	private static String resultName(Class<?> clzz) {
		var sn = Objects.requireNonNull(clzz).getSimpleName();
		Assert.hasText(sn, "the simple name should be non-empty!");
		return Character.toString(sn.charAt(0)).toLowerCase() + sn.substring(1);
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
		var result = repo.result(searchableId);
		var textForSearchable = result.text();
		var titleForSearchable = result.title();
		if (StringUtils.hasText(textForSearchable) && StringUtils.hasText(titleForSearchable)) {
			this.index.ingest(titleForSearchable, textForSearchable, Map.of(KEY, searchableId, CLASS, clzz));
		} //
		else {
			this.log.debug("we've got nothing to index " + "for searchable {} with class {}!", searchableId, clzz);
		}
	}

	@Override
	public Collection<RankedSearchResult> search(String query, Map<String, Object> metadata) {
		var results = new LinkedHashSet<RankedSearchResult>();
		var all = this.index.search(query, metadata);
		all.sort(Comparator.comparing(IndexHit::score));
		for (var hit : all) {
			var documentChunk = hit.documentChunk();
			var documentId = documentChunk.documentId();
			var document = this.index.documentById(documentId);
			var resultMetadata = document.metadata();
			Assert.notNull(resultMetadata, () -> "no metadata found for document id " + documentId);
			var clzz = (String) (resultMetadata.getOrDefault(CLASS, null));
			var clzzObj = ReflectionUtils.classForName(clzz);
			var searchableId = ((Number) (resultMetadata.getOrDefault(KEY, null))).longValue();
			var repo = Objects.requireNonNull(this.repositoryFor(clzzObj), "there is no repository for " + clzz + ".");
			var result = repo.result(searchableId);
			var resultType = resultName(clzzObj);
			log.info("the title is " + result.title());
			var rankedResult = new RankedSearchResult(searchableId, result.aggregate().id(), result.title(),
					result.text(), resultType, hit.score());
			results.add(rankedResult);
		}

		return this.dedupeBySearchableAndType(results);
	}

	private List<RankedSearchResult> dedupeBySearchableAndType(LinkedHashSet<RankedSearchResult> results) {
		var all = new ArrayList<RankedSearchResult>();
		var map = new ConcurrentHashMap<Long, List<RankedSearchResult>>();
		for (var rsr : results) {
			map.computeIfAbsent(rsr.aggregateId(), _ -> new ArrayList<>()).add(rsr);
		}
		var comparator = Comparator.comparingDouble(RankedSearchResult::rank);
		for (var k : map.keySet()) {
			map.get(k).stream().max(comparator).ifPresent(all::add);
		}
		return all.stream().sorted(comparator).toList();
	}

	private String keyFor(Class<?> clzz) {
		return clzz != null ? clzz.getName() : null;
	}

	@SuppressWarnings("unchecked")
	@ApplicationModuleListener
	void indexForSearchOnTranscriptCompletion(TranscriptRecordedEvent event) {
		var aClazz = (Class<? extends Transcribable>) event.type();
		var repo = this.repositoryFor(aClazz);
		var transcribable = Objects.requireNonNull(repo).find(event.transcribableId());
		Assert.notNull(transcribable, "the transcribable id " + event.transcribableId() + " was null!");
		this.log.info("indexing for search transcribable ID {}", event.transcribableId());
		this.index(transcribable);
	}

}
