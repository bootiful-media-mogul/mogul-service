package com.joshlong.mogul.api.search.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.joshlong.mogul.api.AbstractDomainService;
import com.joshlong.mogul.api.Searchable;
import com.joshlong.mogul.api.SearchableResolver;
import com.joshlong.mogul.api.SearchableResult;
import com.joshlong.mogul.api.search.SearchService;
import com.joshlong.mogul.api.transcripts.TranscriptRecordedEvent;
import com.joshlong.mogul.api.utils.ReflectionUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;

interface DocumentRepository extends ElasticsearchRepository<Document, String> {

}

@Service
class ElasticSearchService extends AbstractDomainService<Searchable, SearchableResolver<?>> implements SearchService {

	static final String INDEX_NAME = "searchables";

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final DocumentRepository documentRepository;

	private final ElasticsearchOperations ops;

	ElasticSearchService(Collection<SearchableResolver<?>> resolvers, DocumentRepository documentRepository,
			ElasticsearchOperations ops) {
		super(resolvers);
		this.documentRepository = documentRepository;
		this.ops = ops;
	}

	private static String keyFor(@Nullable Class<?> clzz) {
		return clzz != null ? clzz.getName() : null;
	}

	@Override
	public <T extends Searchable> void index(T searchable) {
		this.log.info("indexing for searchable {}", searchable);
		var searchableId = searchable.searchableId();
		var clzz = keyFor(searchable.getClass());
		var repo = findResolver(searchable.getClass());
		Assert.notNull(repo, () -> "there's no repository for " + clzz + "!");
		var results = repo.results(List.of(searchableId));
		if (!results.isEmpty()) {
			var result = results.getFirst();
			var text = result.text();
			var title = result.title();
			if (StringUtils.hasText(title)) {
				var document = new Document(Long.toString(searchableId), searchableId, title, Instant.now(),
						StringUtils.hasText(text) ? text : "", clzz);
				this.documentRepository.save(document);
			} //
			else {
				this.log.debug("we've got nothing to index for" + " searchable {} with class {}!", searchableId, clzz);
			}
		}
	}

	@Override
	public Collection<SearchableResult<? extends Searchable>> search(String shouldContain,
			Map<String, Object> metadata) {
		this.log.info("searching for [{}] with metadata {}", shouldContain, metadata);
		var nativeQuery = NativeQuery.builder() //
			.withQuery(q -> q //
				.bool(b -> {
					// Must match this term
					b.must(m -> m.multiMatch(
							mm -> mm.query(shouldContain).fields("title^2", "description^2").fuzziness("AUTO")));

					// Should match (boosts relevance if present)
					if (StringUtils.hasText(shouldContain)) {
						b.should(s -> s.match(mt -> mt.field("description").query(shouldContain)));
					}
					return b;
				})) //
			.withMaxResults(1000) //
			.build();
		var search = this.ops.search(nativeQuery, Document.class);
		return this.mapDocumentsToSearchables(search);
	}

	@SuppressWarnings("unchecked")
	private List<SearchableResult<? extends Searchable>> mapDocumentsToSearchables(SearchHits<Document> search) {
		var searchableToRank = new HashMap<Long, Float>();
		var resultsBucketedByClass = new HashMap<Class<?>, List<Document>>();
		var classToResolvers = new HashMap<Class<?>, SearchableResolver<?>>();
		for (var doc : search) {
			var clzz = (Class<Searchable>) ReflectionUtils.classForName(doc.getContent().className());
			resultsBucketedByClass.computeIfAbsent(clzz, _ -> new ArrayList<>()).add(doc.getContent());
			classToResolvers.computeIfAbsent(clzz, _ -> this.findResolver(clzz));
			searchableToRank.put(doc.getContent().searchableId(), doc.getScore());
		}
		var map = new HashMap<Long, ArrayList<SearchableResult<? extends Searchable>>>();
		for (var entry : resultsBucketedByClass.entrySet()) {
			var docs = entry.getValue();
			var clazz = entry.getKey();
			var searchableIds = docs.stream().map(Document::searchableId).toList();
			var resolver = classToResolvers.get(clazz);
			Assert.notNull(resolver, "there must be a valid resolver for " + clazz);
			for (var rs : resolver.results(searchableIds)) {
				map.computeIfAbsent(rs.aggregateId(), _ -> new ArrayList<>()).add(rs);
			}
		}
		var results = new ArrayList<SearchableResult<? extends Searchable>>();
		for (var aggregateToSearchableEntry : map.entrySet()) {
			aggregateToSearchableEntry.getValue()
				.stream()
				.max(Comparator.comparing(SearchableResult::rank))
				.ifPresent(results::add);
		}
		return results.stream()
			.map((Function<SearchableResult<? extends Searchable>, SearchableResult<? extends Searchable>>) sr -> new SearchableResult<>(
					sr.searchableId(), sr.searchable(), sr.title(), sr.text(), sr.aggregateId(), sr.context(),
					sr.created(), searchableToRank.getOrDefault(sr.searchableId(), 0.0f), sr.type()))
			.sorted((o1, o2) -> Float.compare(o2.rank(), o1.rank()))
			.peek(this::log)
			.toList();
	}

	private void log(SearchableResult<?> sr) {
		if (this.log.isTraceEnabled())
			this.log.trace("{}:{} {} {}", sr.rank(), sr.searchableId(), sr.title(), sr.context());
	}

	@SuppressWarnings("unchecked")
	@ApplicationModuleListener
	void indexForSearchOnTranscriptCompletion(TranscriptRecordedEvent event) {
		var aClazz = (Class<? extends Searchable>) event.type();
		var repo = this.findResolver(aClazz);
		var transcribable = Objects.requireNonNull(repo).find(event.transcribableId());
		Assert.notNull(transcribable, "the transcribable id " + event.transcribableId() + " was null!");
		this.index(transcribable);
	}

}

@org.springframework.data.elasticsearch.annotations.Document(createIndex = false,
		indexName = ElasticSearchService.INDEX_NAME)
record Document(@Id String id, @Field(type = FieldType.Long) Long searchableId,
		@Field(type = FieldType.Text, analyzer = "english") String title,
		@Field(type = FieldType.Date, format = DateFormat.epoch_millis) Instant when,
		@Field(type = FieldType.Text, analyzer = "english") String description,
		@Field(type = FieldType.Text) String className) {
}
