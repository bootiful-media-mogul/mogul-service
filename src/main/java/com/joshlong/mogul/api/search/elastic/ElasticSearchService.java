package com.joshlong.mogul.api.search.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.joshlong.mogul.api.AbstractDomainService;
import com.joshlong.mogul.api.Searchable;
import com.joshlong.mogul.api.SearchableResolver;
import com.joshlong.mogul.api.search.RankedSearchResult;
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
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


interface DocumentRepository extends ElasticsearchRepository<Document, String> {
}

@Service
class ElasticSearchService
        extends AbstractDomainService<Searchable, SearchableResolver<?>>
        implements SearchService {

    static final String INDEX_NAME = "searchables";

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final DocumentRepository documentRepository;

    private final ElasticsearchOperations ops;

    private final ElasticsearchClient client;

    ElasticSearchService(Collection<SearchableResolver<?>> resolvers, DocumentRepository documentRepository,
                         ElasticsearchOperations ops, ElasticsearchClient client) {
        super(resolvers);
        this.documentRepository = documentRepository;
        this.client = client;
        this.ops = ops;
    }

    @Override
    public void reset() {
        this.log.warn("resetting the search index!");
        var indices = client.indices();
        try {
            if (indices.exists(index -> index.index(INDEX_NAME)).value()) {
                indices.delete(index -> index.index(INDEX_NAME));
            }
            indices.create(index -> index.index(INDEX_NAME));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String resultName(Class<?> clzz) {
        var sn = Objects.requireNonNull(clzz).getSimpleName();
        Assert.hasText(sn, "the simple name should be non-empty!");
        return Character.toString(sn.charAt(0)).toLowerCase() + sn.substring(1);
    }

    private static String keyFor(@Nullable Class<?> clzz) {
        return clzz != null ? clzz.getName() : null;
    }

    @Override
    public <T extends Searchable> void index(T searchable) {
        log.info("indexing for searchable {}", searchable);
        var searchableId = searchable.searchableId();
        var clzz = keyFor(searchable.getClass());
        var repo = findRepository(searchable.getClass());
        Assert.notNull(repo, () -> "there's no repository for " + clzz + "!");
        var result = repo.result(searchableId);
        var text = result.text();
        var title = result.title();
        if (StringUtils.hasText(title)) {
            var document = new Document(Long.toString(searchableId), searchableId, title, Instant.now(),
                    StringUtils.hasText(text) ? text : "", clzz);
            this.documentRepository.save(document);
        } //
        else {
            this.log.debug("we've got nothing to index for searchable {} with class {}!", searchableId, clzz);
        }
    }

    @Override
    public Collection<RankedSearchResult> search(String shouldContain, Map<String, Object> metadata) {
        this.log.info("searching for {} with metadata {}", shouldContain, metadata);
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
        var results = new ArrayList<RankedSearchResult>();
        this.ops.search(nativeQuery, Document.class)
                .forEach(sh -> results.add(mapDocumentToRankedSearchResult(sh.getContent(), sh.getScore())));
        return this.dedupeBySearchableAndType(results)
                .stream()
                .sorted(Comparator.comparingDouble(RankedSearchResult::rank))
                .toList()
                .reversed();
    }

    private RankedSearchResult mapDocumentToRankedSearchResult(Document document, double rank) {
        var clzz = document.className();
        var clzzObj = (Class<? extends Searchable>) ReflectionUtils.classForName(clzz);
        var searchableId = document.searchableId();
        var repo = Objects.requireNonNull(this.findRepository(clzzObj), "there is no repository for " + clzz + ".");
        Assert.notNull(searchableId, "the searchableId must not be null");
        var result = repo.result(searchableId);
        var resultType = resultName(clzzObj);
        return new RankedSearchResult(searchableId, result.aggregate().id(), result.title(), result.text(), resultType,
                rank, result.created());
    }

    @SuppressWarnings("unchecked")
    @ApplicationModuleListener
    void indexForSearchOnTranscriptCompletion(TranscriptRecordedEvent event) {
        var aClazz = (Class<? extends Searchable>) event.type();
        var repo = this.findRepository(aClazz);
        var transcribable = Objects.requireNonNull(repo).find(event.transcribableId());
        Assert.notNull(transcribable, "the transcribable id " + event.transcribableId() + " was null!");
        this.index(transcribable);
    }

    private List<RankedSearchResult> dedupeBySearchableAndType(List<RankedSearchResult> results) {
        var all = new ArrayList<RankedSearchResult>();
        var map = new ConcurrentHashMap<Long, List<RankedSearchResult>>();
        for (var rsr : results) {
            map.computeIfAbsent(rsr.aggregateId(), _ -> new ArrayList<>()).add(rsr);
        }
        var comparator = Comparator.comparingDouble(RankedSearchResult::rank);
        for (var k : map.keySet()) {
            map.get(k).stream().max(comparator).ifPresent(all::add);
        }
        return all.stream().toList();
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
