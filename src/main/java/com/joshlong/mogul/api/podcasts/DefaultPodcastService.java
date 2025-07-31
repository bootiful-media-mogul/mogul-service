package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.compositions.Composition;
import com.joshlong.mogul.api.compositions.CompositionService;
import com.joshlong.mogul.api.managedfiles.CommonMediaTypes;
import com.joshlong.mogul.api.managedfiles.ManagedFile;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.managedfiles.ManagedFileUpdatedEvent;
import com.joshlong.mogul.api.media.Media;
import com.joshlong.mogul.api.media.MediaNormalizedEvent;
import com.joshlong.mogul.api.mogul.MogulCreatedEvent;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.notifications.NotificationEvent;
import com.joshlong.mogul.api.notifications.NotificationEvents;
import com.joshlong.mogul.api.transcription.TranscriptionCompletedEvent;
import com.joshlong.mogul.api.transcription.TranscriptionService;
import com.joshlong.mogul.api.utils.CacheUtils;
import com.joshlong.mogul.api.utils.CollectionUtils;
import com.joshlong.mogul.api.utils.JdbcUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/*
 * we'll have two different flows: one to handle processing an image, and another to handle processing segments. processing, in this case, refers to things like:
 *
 * <li>resizing images </li>
 * <li>transcoding audio segments</li>
 * <li>creating transcripts</li>
 * <p>
 * Once all these things are done, we'll communicate state changes via events.
 * <p>
 * we need to decouple {@link  PodcastService podcastService} from everything to do with <EM>transcription</EM> or <em>media normalization</em>. the {@link  PodcastService podcastService}
 * handles operations in a transaction. The transcription and normalization should <EM>not</EM> happen in a transaction, as this will block the SQL database and thwart scalability.
 */

@Transactional
class DefaultPodcastService implements PodcastService {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final PodcastRowMapper podcastRowMapper;

	private final CompositionService compositionService;

	private final SegmentRowMapper episodeSegmentRowMapper;

	private final ManagedFileService managedFileService;

	private final Media media;

	private final JdbcClient db;

	private final ApplicationEventPublisher publisher;

	private final MogulService mogulService;

	private final Cache podcastCache, podcastEpisodesCache;

	private final TransactionTemplate transactions;

	// private final TranscriptionService transcriptionService;

	DefaultPodcastService(CompositionService compositionService, Media media, JdbcClient db,
			ManagedFileService managedFileService, ApplicationEventPublisher publisher, Cache podcastCache,
			Cache podcastEpisodesCache, MogulService mogulService, TransactionTemplate transactions) {
		this.podcastEpisodesCache = podcastEpisodesCache;
		this.podcastCache = podcastCache;
		this.compositionService = compositionService;
		this.db = db;
		this.media = media;
		// this.transcriptionService = transcriptionService;
		this.managedFileService = managedFileService;
		this.publisher = publisher;
		this.transactions = transactions;
		this.podcastRowMapper = new PodcastRowMapper();
		this.episodeSegmentRowMapper = new SegmentRowMapper(this.managedFileService::getManagedFileById);
		this.mogulService = mogulService;
	}

	@Override
	public Map<Long, List<Segment>> getPodcastEpisodeSegmentsByEpisodes(Collection<Long> episodes) {
		if (episodes.isEmpty())
			return new HashMap<>();
		var idsAsString = episodes.stream().map(e -> Long.toString(e)).collect(Collectors.joining(", "));
		var segments = db
			.sql("select * from podcast_episode_segment pes where pes.podcast_episode_id in (" + idsAsString + ") ")
			.query(this.episodeSegmentRowMapper)
			.list();
		var episodeToSegmentsMap = new HashMap<Long, List<Segment>>();
		for (var s : segments) {
			episodeToSegmentsMap.computeIfAbsent(s.episodeId(), _ -> new ArrayList<>()).add(s);
		}
		return episodeToSegmentsMap;
	}

	@Override
	public List<Segment> getPodcastEpisodeSegmentsByEpisode(Long episodeId) {
		var sql = " select * from podcast_episode_segment where podcast_episode_id = ? order by sequence_number ASC ";
		var episodeSegmentsFromDb = this.db //
			.sql(sql) //
			.params(episodeId) //
			.query(this.episodeSegmentRowMapper) //
			.stream()//
			.sorted(Comparator.comparingInt(Segment::order))//
			.toList();
		return new ArrayList<>(episodeSegmentsFromDb);
	}

	@ApplicationModuleListener
	void mediaNormalized(MediaNormalizedEvent normalizedEvent) {

		if (normalizedEvent.context().containsKey(PODCAST_EPISODE_CONTEXT_KEY)) {
			// if its a podcast episode
			var episodeId = (Long) normalizedEvent.context().get(PODCAST_EPISODE_CONTEXT_KEY);
			// and if its to do with a podcast episode segment
			this.invalidatePodcastEpisodeCache(episodeId);

			if (normalizedEvent.context().containsKey(PODCAST_EPISODE_SEGMENT_CONTEXT_KEY)) {

				var segmentId = (Long) normalizedEvent.context().get(PODCAST_EPISODE_SEGMENT_CONTEXT_KEY);
				var segment = this.getPodcastEpisodeSegmentById(segmentId);
				this.db.sql("update podcast_episode set produced_audio_assets_updated = ? where id = ? ")
					.params(new Date(), episodeId)
					.update();

				// todo we should kick off the transcription from this point onward.
				// var podcastEpisodeContextKey = Map.of(PODCAST_EPISODE_CONTEXT_KEY,
				// (Object) episodeId,
				// PODCAST_EPISODE_SEGMENT_CONTEXT_KEY, segmentId);
				// this.transcriptionService.transcribe(normalizedEvent.in().mogulId(),
				// segment, podcastEpisodeContextKey);
			}

			this.refreshPodcastEpisodeCompleteness(episodeId);
			this.publisher.publishEvent(new PodcastEpisodeUpdatedEvent(this.getPodcastEpisodeById(episodeId)));

		}

	}

	private static final String PODCAST_EPISODE_CONTEXT_KEY = "podcastEpisodeId";

	private static final String PODCAST_EPISODE_SEGMENT_CONTEXT_KEY = "podcastEpisodeSegmentId";

	private static final String PODCAST_EPISODE_GRAPHIC_CONTEXT_KEY = "podcastEpisodeGraphicId";

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
			this.media.normalize(episode.graphic(), episode.producedGraphic(), podcastEpisodeContext);
		} //
		else {
			// or it's one of the segments
			for (var segment : segments) {
				if (segment.audio().id().equals(mf.id())) {
					var podcastEpisodeSegmentContext = Map.of( //
							PODCAST_EPISODE_CONTEXT_KEY, (Object) episodeId, //
							PODCAST_EPISODE_SEGMENT_CONTEXT_KEY, segment.id() //
					);
					this.media.normalize(segment.audio(), segment.producedAudio(), podcastEpisodeSegmentContext);
				}
			}
		}

	}

	/*
	 * todo could we move this into the transcription service ? arent we already listening
	 * for this event and recording the change in a generic fashion elsewhere? todo we
	 * ARE! its in the transcriptionservice! so.... why couldnt we also just publish a
	 * NotificationEvent and be done with it?
	 *
	 * @ApplicationModuleListener void
	 * onPodcastEpisodeSegmentTranscription(TranscriptionCompletedEvent processedEvent)
	 * throws Exception { var key = processedEvent.key(); var txt = processedEvent.text();
	 * var clzz = processedEvent.type(); var mogulId = processedEvent.mogulId(); if
	 * (StringUtils.hasText(txt) && clzz.getName().equals(Segment.class.getName()) && key
	 * instanceof Number number) { var id = number.longValue();
	 * this.setPodcastEpisodeSegmentTranscript(id, true, txt); }
	 *
	 * var notificationEvent = NotificationEvent.systemNotificationEventFor(mogulId,
	 * processedEvent, processedEvent.key().toString(), processedEvent.text());
	 * NotificationEvents.notify(notificationEvent); }
	 */

	private void refreshPodcastEpisodeCompleteness(Long episodeId) {
		this.transactions.execute(_ -> {
			doBroadcast(episodeId);
			return null;
		});
	}

	private void doBroadcast(Long episodeId) {
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
				detailsOnSegments.append(s.id())
					.append(": written audio? ")
					.append(s.audio().written())
					.append(" produced audio? ")
					.append(s.producedAudio().written())
					.append("\n");
			}
		}

		// let's build up a report to help us understand what's gone wrong
		// it would be nice to produce a publication report or something

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
		return this.db//
			.sql(" select * from podcast_episode pe where pe.podcast_id  = ? ") //
			.param(podcastId)//
			.query(episodeRowMapper)//
			.list();
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
		var all = getAllPodcastEpisodesByIds(List.of(episodeId));
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
		this.db.sql(" delete from podcast where id = ?").param(podcastId).update();
		this.invalidatePodcastCache(podcastId);
		this.publisher.publishEvent(new PodcastDeletedEvent(podcast));
	}

	private void invalidatePodcastEpisodeCache(Long episodeId) {
		this.podcastEpisodesCache.evictIfPresent(episodeId);
	}

	private void invalidatePodcastCache(Long podcastId) {
		this.podcastCache.evict(podcastId);
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
		// var bucket = PodcastService.PODCAST_EPISODES_BUCKET;
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
		this.invalidatePodcastEpisodeCache(episodeId);
		return this.getPodcastEpisodeSegmentById(id.longValue());
	}

	/*
	 * //todo this code needs to exist, perhaps in the repository? SOMEWHERE, surely. can
	 * we move the resolution of the Repositories to the controller? // could we have a
	 * transcriptionController that handles updating transcriptions for all things in a
	 * generic fashion? // @Override public void setPodcastEpisodeSegmentTranscript(Long
	 * episodeSegmentId, boolean transcribable, String transcript) { var segment =
	 * this.getPodcastEpisodeSegmentById(episodeSegmentId); if (null != segment) { var
	 * updated = this.db
	 * .sql("update podcast_episode_segment set transcript = ?, transcribable = ?  where id = ? "
	 * ) .params(transcript, transcribable, segment.id()) .update(); Assert.state(updated
	 * != 0, "there should be at least " + "one transcript set for segment # " +
	 * segment.id()); var podcastEpisodeId = db
	 * .sql("select pes.podcast_episode_id from podcast_episode_segment pes where pes.id =?"
	 * ) .param(episodeSegmentId) .query((rs, _) -> rs.getLong("podcast_episode_id"))
	 * .single(); this.invalidatePodcastEpisodeCache(podcastEpisodeId); } // else {
	 * this.log.debug("could not find the podcast episode segment with id: {} ",
	 * episodeSegmentId); } }
	 *
	 * @Override public void transcribePodcastEpisodeSegment(Long episodeSegmentId) {
	 * this.log.debug("going to refresh the transcription for segment {}. ",
	 * episodeSegmentId); var segment =
	 * this.getPodcastEpisodeSegmentById(episodeSegmentId); var mogul =
	 * this.mogulService.getCurrentMogul().id(); if (null != segment) {// todo //
	 * this.transcribe(mogul, segment.id(), Segment.class, //
	 * this.managedFileService.read(segment.producedAudio().id())); } }
	 */
	@Override
	public Segment getPodcastEpisodeSegmentById(Long episodeSegmentId) {
		return this.db//
			.sql("select * from podcast_episode_segment where id =?")//
			.params(episodeSegmentId)
			.query(this.episodeSegmentRowMapper)//
			.optional()//
			.orElse(null);
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

	@Override
	public Collection<Episode> getAllPodcastEpisodesByIds(Collection<Long> episodeIds) {
		if (episodeIds.isEmpty()) {
			return Set.of();
		}
		var map = new HashMap<Long, Episode>();
		var idsNotInCache = CacheUtils.notPresentInCache(this.podcastEpisodesCache, episodeIds);
		if (!idsNotInCache.isEmpty()) {
			var ids = idsNotInCache.stream().map(e -> Long.toString(e)).collect(Collectors.joining(","));
			var episodes = this.db //
				.sql("select * from podcast_episode pe where pe.id in ( " + ids + " )") //
				.query(new EpisodeRowMapper(true, managedFileService::getManagedFiles)) //
				.list();
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

}
