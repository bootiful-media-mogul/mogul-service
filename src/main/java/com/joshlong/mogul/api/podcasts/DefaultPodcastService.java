package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.compositions.Composition;
import com.joshlong.mogul.api.compositions.CompositionService;
import com.joshlong.mogul.api.managedfiles.CommonMediaTypes;
import com.joshlong.mogul.api.managedfiles.ManagedFile;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.managedfiles.ManagedFileUpdatedEvent;
import com.joshlong.mogul.api.media.MediaNormalizedEvent;
import com.joshlong.mogul.api.media.MediaService;
import com.joshlong.mogul.api.mogul.MogulCreatedEvent;
import com.joshlong.mogul.api.notifications.NotificationEvent;
import com.joshlong.mogul.api.notifications.NotificationEvents;
import com.joshlong.mogul.api.transcripts.TranscriptInvalidatedEvent;
import com.joshlong.mogul.api.transcripts.TranscriptRecordedEvent;
import com.joshlong.mogul.api.utils.CacheUtils;
import com.joshlong.mogul.api.utils.CollectionUtils;
import com.joshlong.mogul.api.utils.JdbcUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Transactional
class DefaultPodcastService implements PodcastService {

	static final String PODCAST_EPISODE_CONTEXT_KEY = "podcastEpisodeId";

	static final String PODCAST_EPISODE_SEGMENT_CONTEXT_KEY = "podcastEpisodeSegmentId";

	static final String PODCAST_EPISODE_GRAPHIC_CONTEXT_KEY = "podcastEpisodeGraphicId";

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final PodcastRowMapper podcastRowMapper;

	private final CompositionService compositionService;

	private final ManagedFileService managedFileService;

	private final MediaService mediaService;

	private final JdbcClient db;

	private final ApplicationEventPublisher publisher;

	private final Cache podcastCache, podcastEpisodesCache;

	private final TransactionTemplate transactions;

	private final Comparator<Episode> episodeComparator = Comparator.comparing(Episode::created).reversed();

	DefaultPodcastService(CompositionService compositionService, MediaService mediaService, JdbcClient db,
			ManagedFileService managedFileService, ApplicationEventPublisher publisher, Cache podcastCache,
			Cache podcastEpisodesCache, TransactionTemplate transactions) {
		this.podcastEpisodesCache = podcastEpisodesCache;
		this.podcastCache = podcastCache;
		this.compositionService = compositionService;
		this.db = db;
		this.mediaService = mediaService;
		this.managedFileService = managedFileService;
		this.publisher = publisher;
		this.transactions = transactions;
		this.podcastRowMapper = new PodcastRowMapper();
	}

	static class SegmentResultSetExtractor implements ResultSetExtractor<List<Segment>> {

		private final Function<Collection<Long>, Map<Long, ManagedFile>> managedFileFunction;

		SegmentResultSetExtractor(Function<Collection<Long>, Map<Long, ManagedFile>> managedFileFunction) {
			this.managedFileFunction = managedFileFunction;
		}

		@Override
		public List<Segment> extractData(ResultSet rs) throws SQLException, DataAccessException {

			var list = new ArrayList<Map<String, Object>>();
			var managedFileIds = new HashSet<Long>();

			while (rs.next()) {
				var segmentAudioManagedFileId = rs.getLong("segment_audio_managed_file_id");
				var segmentAudioManagedFileId1 = rs.getLong("produced_segment_audio_managed_file_id");
				managedFileIds.add(segmentAudioManagedFileId);
				managedFileIds.add(segmentAudioManagedFileId1);
				list.add(Map.of("podcast_episode_id", rs.getLong("podcast_episode_id"), //
						"id", rs.getLong("id"), "segment_audio_managed_file_id", segmentAudioManagedFileId,
						"produced_segment_audio_managed_file_id", segmentAudioManagedFileId1, "cross_fade_duration",
						rs.getLong("cross_fade_duration"), "name", rs.getString("name"), "sequence_number",
						rs.getInt("sequence_number")));
			}

			var managedFileMap = managedFileFunction.apply(managedFileIds);
			var result = new ArrayList<Segment>();
			for (var m : list) {
				result.add(new Segment((Long) m.get("podcast_episode_id"), (Long) m.get("id"),
						managedFileMap.get((Long) m.get("segment_audio_managed_file_id")),
						managedFileMap.get((Long) m.get("produced_segment_audio_managed_file_id")),
						(Long) m.get("cross_fade_duration"), (String) m.get("name"),
						(Integer) m.get("sequence_number")));
			}
			return result;
		}

	}

	private static Long[] array(Collection<Long> longs) {
		var a = new Long[longs.size()];
		var i = 0;
		for (var l : longs) {
			a[i] = l;
			i++;
		}
		return a;
	}

	@Override
	public Map<Long, List<Segment>> getPodcastEpisodeSegmentsByEpisodes(Collection<Long> episodes) {

		if (episodes.isEmpty()) {
			return new HashMap<>();
		}

		var segmentResultSetExtractor = new SegmentResultSetExtractor(managedFileService::getManagedFiles);
		var segments = db.sql(
				"select * from podcast_episode_segment pes where pes.podcast_episode_id = any(?) order by sequence_number ASC ")
			.params(new SqlArrayValue("bigint", (Object[]) array(episodes)))
			.query(segmentResultSetExtractor);
		var episodeToSegmentsMap = new HashMap<Long, List<Segment>>();
		var comparator = Comparator.comparingInt(Segment::order);
		for (var s : segments) {
			episodeToSegmentsMap.computeIfAbsent(s.episodeId(), _ -> new ArrayList<>()).add(s);
		}
		for (var entry : episodeToSegmentsMap.entrySet()) {
			entry.getValue().sort(comparator);
		}
		return episodeToSegmentsMap;
	}

	@Override
	public List<Segment> getPodcastEpisodeSegmentsByEpisode(Long episodeId) {

		var all = getPodcastEpisodeSegmentsByIds(List.of(episodeId));
		return new ArrayList<>(all);

		/*
		 * var sql =
		 * " select * from podcast_episode_segment where podcast_episode_id = ? order by sequence_number ASC "
		 * ; var episodeSegmentsFromDb = this.db // .sql(sql) // .params(episodeId) //
		 * .query(this.episodeSegmentRowMapper) // .stream()//
		 * .sorted(Comparator.comparingInt(Segment::order))// .toList(); return new
		 * ArrayList<>(episodeSegmentsFromDb);
		 */
	}

	private void triggerTranscription(Long mogulId, Long segmentId) {
		this.publisher.publishEvent(new TranscriptInvalidatedEvent(mogulId, segmentId, Segment.class, Map.of()));
	}

	@ApplicationModuleListener
	void invalidateCacheBecauseOfTranscriptUpdates(TranscriptRecordedEvent recordedEvent) {
		this.log.info("you've got your transcript, invalidate ur cache for podcast episodes!");
	}

	@ApplicationModuleListener
	void mediaNormalized(MediaNormalizedEvent normalizedEvent) {
		if (normalizedEvent.context().containsKey(PODCAST_EPISODE_CONTEXT_KEY)) {
			var episodeId = (Long) normalizedEvent.context().get(PODCAST_EPISODE_CONTEXT_KEY);
			this.invalidatePodcastEpisodeCache(episodeId);
			if (normalizedEvent.context().containsKey(PODCAST_EPISODE_SEGMENT_CONTEXT_KEY)) {
				var segmentId = (Long) normalizedEvent.context().get(PODCAST_EPISODE_SEGMENT_CONTEXT_KEY);
				this.db.sql("update podcast_episode set produced_audio_assets_updated = ? where id = ? ")
					.params(new Date(), episodeId)
					.update();
				this.triggerTranscription(normalizedEvent.in().mogulId(), segmentId);
			}
			this.refreshPodcastEpisodeCompleteness(episodeId);
			this.publisher.publishEvent(new PodcastEpisodeUpdatedEvent(this.getPodcastEpisodeById(episodeId)));
		}

	}

	@ApplicationModuleListener
	void podcastManagedFileUpdated(ManagedFileUpdatedEvent managedFileUpdatedEvent) throws Exception {
		var mf = managedFileUpdatedEvent.managedFile();
		var sql = """
				select pes.podcast_episode_id  as id
				from podcast_episode_segment pes
				where pes.segment_audio_managed_file_id  = ?
				UNION
				select pe.id as id
				from podcast_episode pe
				where pe.graphic_managed_file_id = ?
				""";
		var episodeId = CollectionUtils
			.firstOrNull(this.db.sql(sql).params(mf.id(), mf.id()).query((rs, _) -> rs.getLong("id")).set());
		if (episodeId == null) { // not our problem.
			return;
		}
		this.invalidatePodcastEpisodeCache(episodeId);
		var episode = this.getPodcastEpisodeById(episodeId);
		var segments = this.getPodcastEpisodeSegmentsByEpisode(episodeId);
		if (episode.graphic().id().equals(mf.id())) {
			var podcastEpisodeContext = Map.of(PODCAST_EPISODE_CONTEXT_KEY, (Object) episodeId, //
					PODCAST_EPISODE_GRAPHIC_CONTEXT_KEY, episode.graphic().id() //
			);
			this.mediaService.normalize(episode.graphic(), episode.producedGraphic(), podcastEpisodeContext);
		} //
		else {
			// or it's one of the segments
			for (var segment : segments) {
				if (segment.audio().id().equals(mf.id())) {
					var podcastEpisodeSegmentContext = Map.of( //
							PODCAST_EPISODE_CONTEXT_KEY, (Object) episodeId, //
							PODCAST_EPISODE_SEGMENT_CONTEXT_KEY, segment.id() //
					);
					this.mediaService.normalize(segment.audio(), segment.producedAudio(), podcastEpisodeSegmentContext);
				}
			}
		}

	}

	private void refreshPodcastEpisodeCompleteness(Long episodeId) {
		this.transactions.execute(_ -> {
			this.doBroadcastOfEpisodeCompleteness(episodeId);
			return null;
		});
	}

	private void doBroadcastOfEpisodeCompleteness(Long episodeId) {
		this.invalidatePodcastEpisodeCache(episodeId);
		var episode = this.getPodcastEpisodeById(episodeId);
		var mogulId = episode.producedAudio().mogulId(); // hacky.
		var segments = this.getPodcastEpisodeSegmentsByEpisode(episodeId);
		var graphicsWritten = episode.graphic().written() && episode.producedGraphic().written();
		var allSegmentsHaveWrittenAndProducedAudio = segments.stream()
			.allMatch(se -> se.audio().written() && se.producedAudio().written());
		var complete = StringUtils.hasText(episode.title()) && StringUtils.hasText(episode.description())
				&& graphicsWritten && !segments.isEmpty() && allSegmentsHaveWrittenAndProducedAudio;
		this.db.sql("update podcast_episode set complete = ? where id = ? ").params(complete, episode.id()).update();
		this.invalidatePodcastEpisodeCache(episodeId);
		var episodeById = this.getPodcastEpisodeById(episode.id());
		var detailsOnSegments = new StringBuilder();
		if (!allSegmentsHaveWrittenAndProducedAudio) {
			for (var s : segments) {
				detailsOnSegments //
					.append(s.id()) //
					.append(": written audio? ") //
					.append(s.audio().written()) //
					.append(" produced audio? ")//
					.append(s.producedAudio().written()) //
					.append("\n");
			}
		}

		var msg = Map.of("graphic written", graphicsWritten, "graphic produced", episode.producedGraphic().written(),
				"segments not empty?", !segments.isEmpty(), "has a title", StringUtils.hasText(episode.title()),
				"all segments have written and produced audio", allSegmentsHaveWrittenAndProducedAudio,
				"details on segments", detailsOnSegments.toString());
		var finalMsg = new StringBuilder();
		for (var k : msg.keySet())
			finalMsg.append(k).append(' ').append(msg.get(k)).append(System.lineSeparator());
		this.log.info(finalMsg.toString());

		for (var e : Set.of(new PodcastEpisodeUpdatedEvent(episodeById),
				new PodcastEpisodeCompletedEvent(mogulId, episodeById))) {
			this.publisher.publishEvent(e);
		}
	}

	@ApplicationModuleListener
	void mogulCreated(MogulCreatedEvent createdEvent) {
		var mogul = createdEvent.mogul();
		if (this.getAllPodcastsByMogul(mogul.id()).isEmpty()) {
			var podcast = this.createPodcast(mogul.id(), mogul.givenName() + " " + mogul.familyName() + "'s Podcast");
			Assert.notNull(podcast,
					"there should be a newly created podcast associated with the mogul [" + mogul + "]");
		}
	}

	/**
	 * returns a graph of all the episodes for a given podcast. if you specify
	 * {@code deep}, then it'll return a highly complicated graph of objects which will
	 * take considerably longer to load (but will have everything)
	 * @param podcastId the id for which you want to load episodes.
	 * @param deep whether to return the full graph of objects or just the results
	 * sufficient to display the search results
	 */
	@Override
	public Collection<Episode> getPodcastEpisodesByPodcast(Long podcastId, boolean deep) {
		var episodeRowMapper = new EpisodeRowMapper(deep, this.managedFileService::getManagedFiles);
		var results = this.db//
			.sql(" select * from podcast_episode pe where pe.podcast_id  = ? ") //
			.param(podcastId)//
			.query(episodeRowMapper)//
			.list();
		results.sort(this.episodeComparator);
		return results;
	}

	@Override
	public Podcast createPodcast(Long mogulId, String title) {
		var generatedKeyHolder = new GeneratedKeyHolder();
		this.db.sql(
				" insert into podcast (mogul_id , title) values (?,?) on conflict on constraint podcast_mogul_id_title_key do update set title = excluded.title ")
			.params(mogulId, title)
			.update(generatedKeyHolder);
		var id = JdbcUtils.getIdFromKeyHolder(generatedKeyHolder);
		var podcast = this.getPodcastById(id.longValue());
		this.publisher.publishEvent(new PodcastCreatedEvent(podcast));
		return podcast;
	}

	@Override
	public Podcast updatePodcast(Long podcastId, String title) {
		this.db.sql(" update podcast set title = ? where id = ? ").params(title, podcastId).update();
		this.invalidatePodcastCache(podcastId);
		var podcast = this.getPodcastById(podcastId);
		Assert.state((null != podcast.title() && title != null), "you must provide a valid title");
		Assert.state(title.equals(podcast.title()), "you must provide a valid title");
		this.invalidatePodcastCache(podcastId);
		this.publisher.publishEvent(new PodcastUpdatedEvent(podcast));
		return podcast;
	}

	@Override
	public Episode createPodcastEpisode(Long podcastId, String title, String description, ManagedFile graphic,
			ManagedFile producedGraphic, ManagedFile producedAudio) {
		Assert.notNull(podcastId, "the podcast is null");
		Assert.notNull(graphic, "the graphic is null ");
		Assert.notNull(producedAudio, "the produced audio is null ");
		Assert.notNull(producedGraphic, "the produced graphic is null");
		var kh = new GeneratedKeyHolder();
		this.db.sql("""
				insert into podcast_episode(
				podcast_id,
				title,
				description,
				graphic_managed_file_id ,
				produced_graphic_managed_file_id,
				produced_audio_managed_file_id
				)
				values (
				?,
				?,
				?,
				?,
				?,
				?
				)
				""")
			.params(podcastId, title, description, graphic.id(), producedGraphic.id(), producedAudio.id())
			.update(kh);
		var id = JdbcUtils.getIdFromKeyHolder(kh);
		var episodeId = id.longValue();
		var episode = this.getPodcastEpisodeById(episodeId);
		this.invalidatePodcastEpisodeCache(episodeId);
		this.publisher.publishEvent(new PodcastEpisodeCreatedEvent(episode));
		return episode;
	}

	@Override
	public Episode getPodcastEpisodeById(Long episodeId) {
		var all = this.getAllPodcastEpisodesByIds(List.of(episodeId));
		Assert.notNull(all, "the collection should not be null");
		if (all.isEmpty())
			return null;
		return all.iterator().next();
	}

	private void updateEpisodeSegmentOrder(Long episodeSegmentId, int order) {
		this.db //
			.sql("update podcast_episode_segment set sequence_number = ? where id = ?")
			.params(order, episodeSegmentId)
			.update();

	}

	private void moveEpisodeSegment(Long episodeId, Long segmentId, int position) {
		var segments = this.getPodcastEpisodeSegmentsByEpisode(episodeId);
		var segment = this.getPodcastEpisodeSegmentById(segmentId);
		var positionOfSegment = segments.indexOf(segment);
		var newPositionOfSegment = positionOfSegment + position;
		if (newPositionOfSegment < 0 || newPositionOfSegment > (segments.size() - 1)) {
			this.log.debug("you're trying to move out of bounds");
			return;
		}
		segments.remove(segment);
		segments.add(newPositionOfSegment, segment);
		this.reorderSegments(segments);
		var epId = this.getPodcastEpisodeSegmentById(segmentId).episodeId();
		this.markAssetsDirty(episodeId);
		this.invalidatePodcastEpisodeCache(epId);
		var ep = this.getPodcastEpisodeById(epId);
		this.publisher.publishEvent(new PodcastEpisodeUpdatedEvent(ep));
	}

	private void reorderSegments(List<Segment> segments) {
		var counter = 0;
		for (var segment : segments) {
			counter += 1;
			this.updateEpisodeSegmentOrder(segment.id(), counter);
		}
	}

	@Override
	public void movePodcastEpisodeSegmentDown(Long episode, Long segment) {
		this.moveEpisodeSegment(episode, segment, 1);
	}

	@Override
	public void movePodcastEpisodeSegmentUp(Long episode, Long segment) {
		this.moveEpisodeSegment(episode, segment, -1);
	}

	@Override
	public void deletePodcastEpisodeSegment(Long episodeSegmentId) {
		var segment = this.getPodcastEpisodeSegmentById(episodeSegmentId);
		Assert.state(segment != null, "you must specify a valid " + Segment.class.getName());
		var managedFilesToDelete = Set.of(segment.audio().id(), segment.producedAudio().id());
		this.markPodcastEpisodeBySegmentAssetsDirty(episodeSegmentId);
		this.db.sql("delete from podcast_episode_segment where id =?").params(episodeSegmentId).update();
		for (var managedFileId : managedFilesToDelete)
			this.managedFileService.deleteManagedFile(managedFileId);
		this.reorderSegments(this.getPodcastEpisodeSegmentsByEpisode(segment.episodeId()));

		this.refreshPodcastEpisodeCompleteness(segment.episodeId());
	}

	@Override
	public void deletePodcast(Long podcastId) {
		var podcast = this.getPodcastById(podcastId);
		for (var episode : this.getPodcastEpisodesByPodcast(podcastId, true)) {
			this.deletePodcastEpisode(episode.id());
		}
		this.db.sql(" delete from podcast where id = ? ").param(podcastId).update();
		this.invalidatePodcastCache(podcastId);
		this.publisher.publishEvent(new PodcastDeletedEvent(podcast));
	}

	private void invalidatePodcastEpisodeCache(Long episodeId) {
		this.podcastEpisodesCache.evictIfPresent(episodeId);
	}

	private void invalidatePodcastCache(Long podcastId) {
		this.podcastCache.evictIfPresent(podcastId);
	}

	@Override
	public void deletePodcastEpisode(Long episodeId) {
		var segmentsForEpisode = this.getPodcastEpisodeSegmentsByEpisode(episodeId);
		if (segmentsForEpisode == null)
			segmentsForEpisode = new ArrayList<>();

		var episode = this.getPodcastEpisodeById(episodeId);

		var ids = new HashSet<Long>();

		for (var managedFile : new ManagedFile[] { episode.graphic(), episode.producedAudio(),
				episode.producedGraphic() })
			if (managedFile != null)
				ids.add(managedFile.id());

		for (var segment : segmentsForEpisode)
			for (var managedFile : new ManagedFile[] { segment.audio(), segment.producedAudio() })
				if (managedFile != null)
					ids.add(managedFile.id());

		this.db.sql("delete from podcast_episode_segment where podcast_episode_id  = ?").param(episode.id()).update();
		this.db.sql("delete from podcast_episode where id = ?").param(episode.id()).update();

		for (var managedFileId : ids)
			this.managedFileService.deleteManagedFile(managedFileId);

		this.invalidatePodcastEpisodeCache(episodeId);
		this.publisher.publishEvent(new PodcastEpisodeDeletedEvent(episode));
	}

	@Override
	public Podcast getPodcastById(Long podcastId) {
		return this.podcastCache.get(podcastId, () -> this.db //
			.sql("select * from podcast p where p.id=?")//
			.param(podcastId)//
			.query(this.podcastRowMapper)//
			.single());
	}

	@Override
	public Composition getPodcastEpisodeTitleComposition(Long episodeId) {
		return this.compositionFor(episodeId, "title");
	}

	@Override
	public Composition getPodcastEpisodeDescriptionComposition(Long episodeId) {
		return this.compositionFor(episodeId, "description");
	}

	private Composition compositionFor(Long episodeId, String field) {
		var episode = this.getPodcastEpisodeById(episodeId);
		return this.compositionService.compose(episode, field);
	}

	@Override
	public Segment createPodcastEpisodeSegment(Long mogulId, Long episodeId, String name, long crossfade) {
		var maxOrder = (this.db
			.sql("select max( sequence_number) from podcast_episode_segment where podcast_episode_id  = ? ")
			.params(episodeId)
			.query(Number.class)
			.optional()
			.orElse(0)
			.longValue()) + 1;
		var uid = UUID.randomUUID().toString();
		var sql = """
				insert into podcast_episode_segment (
				podcast_episode_id,
				segment_audio_managed_file_id ,
				produced_segment_audio_managed_file_id  ,
				cross_fade_duration,
				name,
				sequence_number
				)
				values(
				?,
				?,
				?,
				?,
				?,
				?
				);
				""";
		var segmentAudioManagedFile = this.managedFileService.createManagedFile(mogulId, uid, "", 0,
				CommonMediaTypes.MP3, false);
		var producedSegmentAudioManagedFile = this.managedFileService.createManagedFile(mogulId, uid, "", 0,
				CommonMediaTypes.MP3, false);
		var gkh = new GeneratedKeyHolder();
		this.db //
			.sql(sql)
			.params(episodeId, segmentAudioManagedFile.id(), producedSegmentAudioManagedFile.id(), crossfade, name,
					maxOrder)
			.update(gkh);
		var id = JdbcUtils.getIdFromKeyHolder(gkh);
		this.invalidatePodcastEpisodeCache(episodeId);
		var episodeSegmentsByEpisode = this.getPodcastEpisodeSegmentsByEpisode(episodeId);
		this.reorderSegments(episodeSegmentsByEpisode);
		this.refreshPodcastEpisodeCompleteness(episodeId);
		this.markAssetsDirty(episodeId);
		this.invalidatePodcastEpisodeCache(episodeId);
		return this.getPodcastEpisodeSegmentById(id.longValue());
	}

	private void markPodcastEpisodeBySegmentAssetsDirty(Long podcastEpisodeSegmentId) {
		var pes = this.db.sql("select pes.podcast_episode_id pid from podcast_episode_segment pes  where pes.id = ?")
			.params(podcastEpisodeSegmentId)
			.query((rs, _) -> rs.getLong("pid"))
			.single();
		this.markAssetsDirty(pes);
	}

	/**
	 * any deletion, update, or re-ordering should result in a dirty
	 * produced_audio_assets_updated field
	 */
	private void markAssetsDirty(Long episodeId) {
		log.info("marking the produced_audio_assets_updated = now() for episode_id = {}", episodeId);
		this.db.sql("update podcast_episode set produced_audio_assets_updated  = now() where id   = ?")
			.params(episodeId)
			.update();
	}

	@Override
	public Segment getPodcastEpisodeSegmentById(Long episodeSegmentId) {
		var list = this.db//
			.sql("select * from podcast_episode_segment where id =?")//
			.params(episodeSegmentId)
			.query(new SegmentResultSetExtractor(managedFileService::getManagedFiles));
		if (list.isEmpty())
			return null;
		return list.getFirst();

	}

	@Override
	public Collection<Segment> getPodcastEpisodeSegmentsByIds(List<Long> episodeSegmentIds) {
		if (episodeSegmentIds.isEmpty())
			return new ArrayList<>();
		var arr = new Long[episodeSegmentIds.size()];
		for (var i = 0; i < episodeSegmentIds.size(); i++) {
			arr[i] = episodeSegmentIds.get(i);
		}
		return db.sql("select * from podcast_episode_segment where id = any(?) ") //
			.params(new SqlArrayValue("bigint", (Object[]) arr))//
			.query(new SegmentResultSetExtractor(managedFileService::getManagedFiles));
	}

	@Override
	public Episode createPodcastEpisodeDraft(Long currentMogulId, Long podcastId, String title, String description) {
		this.ensurePodcastBelongsToMogul(currentMogulId, podcastId);
		var uid = UUID.randomUUID().toString();
		var image = this.managedFileService.createManagedFile(currentMogulId, uid, "", 0, CommonMediaTypes.BINARY,
				true);
		var producedGraphic = this.managedFileService.createManagedFile(currentMogulId, uid, "produced-graphic.jpg", 0,
				CommonMediaTypes.JPG, true);
		var producedAudio = this.managedFileService.createManagedFile(currentMogulId, uid, "produced-audio.mp3", 0,
				CommonMediaTypes.MP3, true);
		var episode = this.createPodcastEpisode(podcastId, title, description, image, producedGraphic, producedAudio);
		var episodeId = episode.id();
		var titleComp = this.getPodcastEpisodeTitleComposition(episodeId);
		var descriptionComp = this.getPodcastEpisodeDescriptionComposition(episodeId);
		Assert.notNull(titleComp, "the title composition must not be null");
		Assert.notNull(descriptionComp, "the description composition must not be null");
		var seg = this.createPodcastEpisodeSegment(currentMogulId, episodeId, "", 0);
		Assert.notNull(seg, "could not create a podcast episode segment for episode " + episodeId);
		this.invalidatePodcastEpisodeCache(episodeId);
		return this.getPodcastEpisodeById(episodeId);
	}

	private void ensurePodcastBelongsToMogul(Long currentMogulId, Long podcastId) {
		var match = this.db.sql("select p.id as id from podcast p where p.id =  ? and p.mogul_id = ?  ")
			.params(podcastId, currentMogulId)
			.query((rs, rowNum) -> rs.getInt("id"))
			.list();
		Assert.state(!match.isEmpty(), "there is indeed a podcast with this id and this mogul");
	}

	@Override
	public Episode updatePodcastEpisodeDetails(Long episodeId, String title, String description) {
		Assert.notNull(episodeId, "the episode is null");
		title = StringUtils.hasText(title) ? title : "";
		description = StringUtils.hasText(description) ? description : "";
		this.db.sql("update podcast_episode set title = ?, description =? where id = ?")
			.params(title, description, episodeId)
			.update();
		this.invalidatePodcastEpisodeCache(episodeId);
		this.refreshPodcastEpisodeCompleteness(episodeId);
		var podcastEpisodeById = this.getPodcastEpisodeById(episodeId);
		this.publisher.publishEvent(new PodcastEpisodeUpdatedEvent(podcastEpisodeById));
		return podcastEpisodeById;
	}

	@Override
	public void writePodcastEpisodeProducedAudio(Long episodeId, Long managedFileId) {
		try {
			this.managedFileService.refreshManagedFile(managedFileId);
			this.db //
				.sql("update podcast_episode set produced_audio_updated=? where id = ? ") //
				.params(new Date(), episodeId) //
				.update();
			this.invalidatePodcastEpisodeCache(episodeId);
			this.log.debug("updated episode {} to have non-null produced_audio_updated", episodeId);
			this.publisher.publishEvent(new PodcastEpisodeUpdatedEvent(getPodcastEpisodeById(episodeId)));
		} //
		catch (Throwable throwable) {
			throw new RuntimeException("got an exception dealing with " + throwable.getLocalizedMessage(), throwable);
		}
	}

	static class EpisodeResultSetExtractor implements ResultSetExtractor<Collection<Episode>> {

		private final Function<Collection<Long>, Map<Long, ManagedFile>> managedFileService;

		EpisodeResultSetExtractor(Function<Collection<Long>, Map<Long, ManagedFile>> managedFileService) {
			this.managedFileService = managedFileService;
		}

		@Override
		public Collection<Episode> extractData(ResultSet resultSet) throws SQLException, DataAccessException {

			var results = new ArrayList<Episode>();
			var maps = new ArrayList<Map<String, Object>>();
			var managedFiles = new HashSet<Long>();
			while (resultSet.next()) {
				var map = new HashMap<String, Object>();
				map.put("id", resultSet.getLong("id"));
				map.put("graphic_managed_file_id", resultSet.getLong("graphic_managed_file_id"));
				map.put("produced_graphic_managed_file_id", resultSet.getLong("produced_graphic_managed_file_id"));
				map.put("produced_audio_managed_file_id", resultSet.getLong("produced_audio_managed_file_id"));
				map.put("podcast_id", resultSet.getLong("podcast_id"));
				map.put("title", resultSet.getString("title"));
				map.put("description", resultSet.getString("description"));
				map.put("created", resultSet.getTimestamp("created"));
				map.put("complete", resultSet.getBoolean("complete"));
				map.put("produced_audio_assets_updated", resultSet.getTimestamp("produced_audio_assets_updated"));
				map.put("produced_audio_updated", resultSet.getTimestamp("produced_audio_updated"));

				maps.add(map);
				for (var k : map.keySet()) {
					if (k.contains("managed_file")) {
						managedFiles.add((Long) map.get(k));
					}
				}
			}

			var allManagedFiles = this.managedFileService.apply(managedFiles);

			for (var map : maps) {

				var episodeId = (Long) map.get("id");
				var graphicId = (Long) map.get("graphic_managed_file_id");
				var producedGraphicId = (Long) map.get("produced_graphic_managed_file_id");
				var producedAudioId = (Long) map.get("produced_audio_managed_file_id");
				var graphic = allManagedFiles.get(graphicId);
				var producedGraphic = allManagedFiles.get(producedGraphicId);
				var producedAudio = allManagedFiles.get(producedAudioId);

				var e = new Episode(//
						episodeId, //
						(Long) map.get("podcast_id"), //
						(String) map.get("title"), //
						(String) map.get("description"), //
						(Date) map.get("created"), //
						graphic, //
						producedGraphic, //
						producedAudio, //
						(Boolean) map.get("complete"), //
						(Timestamp) map.get("produced_audio_updated"), //
						(Timestamp) map.get("produced_audio_assets_updated") // ,
				);

				results.add(e);
			}

			return results;
		}

	}

	// todo use a ResultSetExtractor to get the data for ManagedFiles
	// todo create some sort of proxy around ResultSet to memorize the results
	@Override
	public Collection<Episode> getAllPodcastEpisodesByIds(Collection<Long> episodeIds) {
		this.log.info("getting episodes for episode ids(length {}) {}", episodeIds.size(), episodeIds);
		if (episodeIds.isEmpty()) {
			return Set.of();
		}
		var map = new HashMap<Long, Episode>();
		var idsNotInCache = CacheUtils.notPresentInCache(this.podcastEpisodesCache, episodeIds);
		if (!idsNotInCache.isEmpty()) {
			var idsArr = array(idsNotInCache);
			var episodes = this.db //
				.sql("select * from podcast_episode pe where pe.id = any(? )") //
				.params(new SqlArrayValue("bigint", (Object[]) idsArr))
				.query(new EpisodeResultSetExtractor(managedFileService::getManagedFiles));

			for (var episode : episodes) {
				map.put(episode.id(), episode);
			}
		}
		var result = new ArrayList<Episode>();
		for (var id : episodeIds) {
			result.add(this.podcastEpisodesCache.get(id, () -> map.get(id)));
		}
		return result;
	}

	@EventListener
	void podcastDeletedEventNotifyingListener(PodcastDeletedEvent event) {
		var notificationEvent = NotificationEvent.visibleNotificationEventFor(event.podcast().mogulId(), event,
				Long.toString(event.podcast().id()), event.podcast().title());
		NotificationEvents.notify(notificationEvent);
	}

	@EventListener
	void podcastCreatedEventNotifyingListener(PodcastCreatedEvent event) {
		var notificationEvent = NotificationEvent.systemNotificationEventFor(event.podcast().mogulId(), event,
				Long.toString(event.podcast().id()), event.podcast().title());
		NotificationEvents.notify(notificationEvent);
	}

	@Override
	public Collection<Podcast> getAllPodcastsByMogul(Long mogulId) {
		return this.db //
			.sql("select * from podcast p where p.mogul_id = ?")//
			.param(mogulId)//
			.query(this.podcastRowMapper)//
			.list();
	}

	@Override
	public Collection<Podcast> getAllPodcastsById(List<Long> mogulIds) {
		if (null == mogulIds || mogulIds.isEmpty())
			return Set.of();
		var idsArray = new Long[mogulIds.size()];
		for (var i = 0; i < mogulIds.size(); i++)
			idsArray[i] = mogulIds.get(i);
		return db//
			.sql("select * from podcast p where p.id = any(?)")//
			.params(new SqlArrayValue("bigint", (Object[]) idsArray))
			.query(this.podcastRowMapper)//
			.list();
	}

}
