package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.managedfiles.CommonMediaTypes;
import com.joshlong.mogul.api.managedfiles.ManagedFile;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.managedfiles.ManagedFileUpdatedEvent;
import com.joshlong.mogul.api.mogul.MogulCreatedEvent;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.notifications.NotificationEvent;
import com.joshlong.mogul.api.notifications.NotificationEvents;
import com.joshlong.mogul.api.podcasts.production.MediaNormalizationIntegrationRequest;
import com.joshlong.mogul.api.podcasts.production.MediaNormalizer;
import com.joshlong.mogul.api.utils.JdbcUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This service uses a caching scheme that you need to be aware of when effecting changes.
 * All podcasts from all moguls are stored, live, in this class. when there is an update,
 * the entire graph for the mogul is reloaded and kept in {@link ConcurrentHashMap}. The
 * key is to make sure all updates to the SQL DB result in a refreshing of the mogul
 * object graph kept in memory.
 */
@Service
@Transactional
class DefaultPodcastService implements PodcastService {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final PodcastRowMapper podcastRowMapper;

	private final SegmentRowMapper episodeSegmentRowMapper;

	private final MogulService mogulService;

	private final ManagedFileService managedFileService;

	private final MediaNormalizer mediaNormalizer;

	private final JdbcClient db;

	private final ApplicationEventPublisher publisher;

	DefaultPodcastService(MediaNormalizer mediaNormalizer, MogulService mogulService, JdbcClient db,
			ManagedFileService managedFileService, ApplicationEventPublisher publisher) {
		this.db = db;
		this.mediaNormalizer = mediaNormalizer;
		this.mogulService = mogulService;
		this.managedFileService = managedFileService;
		this.publisher = publisher;
		this.podcastRowMapper = new PodcastRowMapper();
		this.episodeSegmentRowMapper = new SegmentRowMapper(this.managedFileService::getManagedFile);
	}

	@Override
	public Map<Long, List<Segment>> getPodcastEpisodeSegmentsByEpisodes(Collection<Long> episodes) {

		Assert.state(!episodes.isEmpty(), "there are no episode segments");
		var idsAsString = episodes.stream().map(e -> Long.toString(e)).collect(Collectors.joining(", "));
		var segments = db
			.sql("select * from podcast_episode_segment pes where pes.podcast_episode_id in (" + idsAsString + ") ")
			.query(this.episodeSegmentRowMapper)
			.list();
		var episodeToSegmentsMap = new HashMap<Long, List<Segment>>();
		for (var s : segments) {
			episodeToSegmentsMap.computeIfAbsent(s.episodeId(), sk -> new ArrayList<>()).add(s);
		}
		return episodeToSegmentsMap;

	}

	@Override
	public List<Segment> getPodcastEpisodeSegmentsByEpisode(Long episodeId) {
		this.log.debug("trying to resolve episode segments for episode #{} from the DB", episodeId);
		var sql = " select * from podcast_episode_segment where podcast_episode_id = ? order by sequence_number ASC ";
		var episodeSegmentsFromDb = this.db //
			.sql(sql) //
			.params(episodeId) //
			.query(this.episodeSegmentRowMapper) //
			.stream()//
			.sorted(Comparator.comparingInt(Segment::order))//
			.toList();// this list is unmodifiable, annoyingly.
		var newList = new ArrayList<>(episodeSegmentsFromDb);
		this.log.debug("episodeSegmentsFromDb length: {} data: {}", newList.size(), newList);
		return newList;
	}

	@EventListener
	void podcastManagedFileUpdated(ManagedFileUpdatedEvent managedFileUpdatedEvent) {
		this.log.debug("good news everyone! we're invoking for managed file #{}",
				managedFileUpdatedEvent.managedFile().id());
		var mf = managedFileUpdatedEvent.managedFile();
		this.log.debug("the managed file content type for id #{} is {}", managedFileUpdatedEvent.managedFile().id(),
				managedFileUpdatedEvent.managedFile().contentType());

		var sql = """
				select pes.podcast_episode_id as id
				from podcast_episode_segment pes
				where pes.segment_audio_managed_file_id = ?
				UNION
				select pe.id as id
				from podcast_episode pe
				where pe.graphic = ?
				""";

		var all = this.db.sql(sql).params(mf.id(), mf.id()).query((rs, rowNum) -> rs.getLong("id")).set();
		if (all.isEmpty())
			return;

		var episodeId = all.iterator().next();
		// var mogul =
		// getPodcastById(this.getEpisodeById(episodeId).podcastId()).mogulId();
		// this.podcasts.remove(mogul);

		var episode = this.getPodcastEpisodeById(episodeId);
		var segments = this.getPodcastEpisodeSegmentsByEpisode(episodeId);

		if (episode.graphic().id().equals(mf.id())) { // either it's the graphic..
			this.mediaNormalizer
				.normalize(new MediaNormalizationIntegrationRequest(episode.graphic(), episode.producedGraphic()));
		} //
		else {
			// or it's one of the segments..
			segments.stream()//
				.filter(s -> s.audio().id().equals(mf.id()))
				.findAny()
				.ifPresent(segment -> {
					var response = this.mediaNormalizer
						.normalize(new MediaNormalizationIntegrationRequest(segment.audio(), segment.producedAudio()));
					Assert.notNull(response, "the response should not be null");
					var updated = new Date();
					// if this is older than the last time we have produced any audio,
					// then we won't reproduce the audio
					this.db.sql("update podcast_episode  set produced_audio_assets_updated = ? where id = ? ")
						.params(updated, episodeId)
						.update();
					this.publisher.publishEvent(new PodcastEpisodeUpdatedEvent(episode));
				});
		}
		// once the file has been normalized, we can worry about completeness
		this.refreshPodcastEpisodeCompleteness(episodeId);

	}

	private void refreshPodcastEpisodeCompleteness(Long episodeId) {
		var episode = this.getPodcastEpisodeById(episodeId);
		var mogulId = episode.producedAudio().mogulId(); // hacky.
		var segments = this.getPodcastEpisodeSegmentsByEpisode(episodeId);
		var graphicsWritten = episode.graphic().written() && episode.producedGraphic().written();
		var complete = graphicsWritten && !segments.isEmpty()
				&& (segments.stream().allMatch(se -> se.audio().written() && se.producedAudio().written()));

		if (this.log.isDebugEnabled()) {

			var message = new StringBuilder();
			message.append("-------------------------------------------------------")
				.append(System.lineSeparator())
				.append(String.format("episode #%s complete? %s", episode.id(), complete))
				.append(System.lineSeparator());

			var managedFileStream = segments.stream()
				.flatMap(seg -> Stream.of(seg.audio(), seg.producedAudio()))
				.filter(mf -> !mf.written())
				.map(ManagedFile::id)
				.map(id -> Long.toString(id))
				.collect(Collectors.toSet());

			if (!managedFileStream.isEmpty()) {
				message.append(String.format("according to this method, the managed files"
						+ " for segments for episode #%s are not written. " + "here are the managed file IDs: %s",
						episodeId, String.join(", ", managedFileStream)))
					.append(System.lineSeparator());
			}
			this.log.debug(message.toString());
		}

		this.db.sql("update podcast_episode set complete = ? where id = ? ").params(complete, episode.id()).update();
		this.log.debug("updating podcast episode #{} to complete = {}", episode.id(), complete);
		var episodeById = this.getPodcastEpisodeById(episode.id());

		if (this.log.isDebugEnabled())
			this.log.debug("the episode #{} is complete complete? {}", episode.id(), episodeById.complete());

		for (var e : Set.of(new PodcastEpisodeUpdatedEvent(episodeById),
				new PodcastEpisodeCompletionEvent(mogulId, episodeById))) {
			this.publisher.publishEvent(e);
			this.log.debug("refreshPodcastEpisodeCompleteness: {}; publishing [{}]", complete, e);
		}
	}

	@ApplicationModuleListener
	void mogulCreated(MogulCreatedEvent createdEvent) {
		var podcast = this.createPodcast(createdEvent.mogul().id(), createdEvent.mogul().username() + "'s Podcast");
		Assert.notNull(podcast,
				"there should be a newly created podcast associated with the mogul [" + createdEvent.mogul() + "]");
	}

	@Override
	public Collection<Episode> getPodcastEpisodesByPodcast(Long podcastId) {
		var podcast = this.getPodcastById(podcastId);
		Assert.notNull(podcast, "the podcast with id [" + podcastId + "] is null");
		var episodeRowMapper = new EpisodeRowMapper(this.managedFileService::getManagedFile,
				episodeId -> new ArrayList<>());
		var eps = this.db//
			.sql(" select * from podcast_episode pe where pe.podcast_id = ? ") //
			.param(podcastId)//
			.query(episodeRowMapper)//
			.list();
		var epIdToEpisode = new HashMap<Long, Episode>();
		for (var e : eps)
			epIdToEpisode.put(e.id(), e);

		var ids = eps.stream().map(Episode::id).toList();
		var segs = this.getPodcastEpisodeSegmentsByEpisodes(ids);

		for (var e : segs.entrySet()) {
			var episodeId = e.getKey();
			var segments = e.getValue();
			epIdToEpisode.get(episodeId).segments().addAll(segments);
		}
		return eps;
	}

	@Override
	public Podcast createPodcast(Long mogulId, String title) {
		var kh = new GeneratedKeyHolder();
		this.db.sql(
				" insert into podcast (mogul_id, title) values (?,?) on conflict on constraint podcast_mogul_id_title_key do update set title = excluded.title ")
			.params(mogulId, title)
			.update(kh);
		var id = JdbcUtils.getIdFromKeyHolder(kh);
		var podcast = this.getPodcastById(id.longValue());
		this.publisher.publishEvent(new PodcastCreatedEvent(podcast));
		return podcast;
	}

	@Override
	public Episode createPodcastEpisode(Long podcastId, String title, String description, ManagedFile graphic,
			ManagedFile producedGraphic, ManagedFile producedAudio) {
		Assert.notNull(podcastId, "the podcast is null");
		Assert.hasText(title, "the title has no text");
		Assert.hasText(description, "the description has no text");
		Assert.notNull(graphic, "the graphic is null ");
		Assert.notNull(producedAudio, "the produced audio is null ");
		Assert.notNull(producedGraphic, "the produced graphic is null");
		var kh = new GeneratedKeyHolder();
		this.db.sql("""
					insert into podcast_episode(
						podcast_id,
						title,
						description,
						graphic ,
						produced_graphic,
						produced_audio
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
		var episode = this.getPodcastEpisodeById(episodeId); // yuck.
		this.publisher.publishEvent(new PodcastEpisodeCreatedEvent(episode));// reset
																				// cache
		return episode;
	}

	@Override
	public Episode getPodcastEpisodeById(Long episodeId) {
		var erm = new EpisodeRowMapper(managedFileService::getManagedFile, this::getPodcastEpisodeSegmentsByEpisode);//
		var res = this.db //
			.sql("select * from podcast_episode where id =?")//
			.param(episodeId)//
			.query(erm) //
			.list();
		return res.isEmpty() ? null : res.getFirst();
	}

	private void updateEpisodeSegmentOrder(Long episodeSegmentId, int order) {
		log.debug("updating podcast_episode_segment [{}] to sequence_number : {}", episodeSegmentId, order);
		this.db //
			.sql("update podcast_episode_segment set sequence_number = ? where id = ?")
			.params(order, episodeSegmentId)
			.update();
		// do not publish an event
	}

	/**
	 * @param position the delta in position: -1 if the item is to be moved earlier in the
	 * collection, +1 if it's to be moved later.
	 */
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
		var ep = this.getPodcastEpisodeById(epId);
		this.publisher.publishEvent(new PodcastEpisodeUpdatedEvent(ep));
	}

	private void reorderSegments(List<Segment> segments) {
		var ctr = 0;
		for (var s : segments) {
			ctr += 1;
			this.updateEpisodeSegmentOrder(s.id(), ctr);
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
		for (var episode : this.getPodcastEpisodesByPodcast(podcastId)) {
			this.deletePodcastEpisode(episode.id());
		}
		this.db.sql("delete from podcast where id= ?").param(podcastId).update();
		this.publisher.publishEvent(new PodcastDeletedEvent(podcast));
	}

	@Override
	public void deletePodcastEpisode(Long episodeId) {
		var segmentsForEpisode = this.getPodcastEpisodeSegmentsByEpisode(episodeId);
		if (segmentsForEpisode == null)
			segmentsForEpisode = new ArrayList<>();

		var func = (BiConsumer<ManagedFile, Set<Long>>) (mf, ids) -> {
			if (mf != null)
				ids.add(mf.id());
		};

		var episode = this.getPodcastEpisodeById(episodeId);

		var ids = new HashSet<Long>();
		func.accept(episode.graphic(), ids);
		func.accept(episode.producedAudio(), ids);
		func.accept(episode.producedGraphic(), ids);

		for (var segment : segmentsForEpisode) {
			func.accept(segment.audio(), ids);
			func.accept(segment.producedAudio(), ids);
		}

		this.db.sql("delete from podcast_episode_segment where podcast_episode_id = ?").param(episode.id()).update();
		this.db.sql("delete from podcast_episode where id = ?").param(episode.id()).update();

		for (var managedFileId : ids)
			this.managedFileService.deleteManagedFile(managedFileId);

		this.publisher.publishEvent(new PodcastEpisodeDeletedEvent(episode));
	}

	@Override
	public Podcast getPodcastById(Long podcastId) {
		return this.db.sql("select * from podcast p where p.id=?")//
			.param(podcastId)//
			.query(podcastRowMapper)//
			.single();
	}

	@Override
	public Segment createPodcastEpisodeSegment(Long mogulId, Long episodeId, String name, long crossfade) {
		var maxOrder = (this.db
			.sql("select max( sequence_number) from podcast_episode_segment where podcast_episode_id = ? ")
			.params(episodeId)
			.query(Number.class)
			.optional()
			.orElse(0)
			.longValue()) + 1;
		var uid = UUID.randomUUID().toString();
		var bucket = PodcastService.PODCAST_EPISODES_BUCKET;
		this.mogulService.assertAuthorizedMogul(mogulId);
		var sql = """
					insert into podcast_episode_segment (
						podcast_episode_id,
						segment_audio_managed_file_id,
						produced_segment_audio_managed_file_id,
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
					) ;
				""";
		var segmentAudioManagedFile = this.managedFileService.createManagedFile(mogulId, bucket, uid, "", 0,
				CommonMediaTypes.MP3);
		var producedSegmentAudioManagedFile = this.managedFileService.createManagedFile(mogulId, bucket, uid, "", 0,
				CommonMediaTypes.MP3);
		var gkh = new GeneratedKeyHolder();
		this.db //
			.sql(sql)
			.params(episodeId, segmentAudioManagedFile.id(), producedSegmentAudioManagedFile.id(), crossfade, name,
					maxOrder)
			.update(gkh);
		var id = JdbcUtils.getIdFromKeyHolder(gkh);
		var episodeSegmentsByEpisode = this.getPodcastEpisodeSegmentsByEpisode(episodeId);
		this.log.debug("episode segments: {}", episodeSegmentsByEpisode);
		this.reorderSegments(episodeSegmentsByEpisode);
		this.refreshPodcastEpisodeCompleteness(episodeId);
		return this.getPodcastEpisodeSegmentById(id.longValue());
	}

	@Override
	public void setPodcastEpisodesSegmentTranscript(Long episodeSegmentId, boolean transcribable, String transcript) {
		var segment = this.getPodcastEpisodeSegmentById(episodeSegmentId);
		if (null != segment) {
			this.db.sql("update podcast_episode_segment set transcript = ?, transcribable = ?  where id = ? ")
				.params(transcript, transcribable, segment.id())
				.update();
		} //
		else {
			this.log.debug("could not find the podcast episode segment with id: {} ", episodeSegmentId);
		}
	}

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
		var uid = UUID.randomUUID().toString();
		var podcast = this.getPodcastById(podcastId);
		Assert.notNull(podcast, "the podcast is null!");
		var bucket = PodcastService.PODCAST_EPISODES_BUCKET;
		var image = this.managedFileService.createManagedFile(currentMogulId, bucket, uid, "", 0,
				CommonMediaTypes.BINARY);
		var producedGraphic = this.managedFileService.createManagedFile(currentMogulId, bucket, uid,
				"produced-graphic.jpg", 0, CommonMediaTypes.JPG);
		var producedAudio = this.managedFileService.createManagedFile(currentMogulId, bucket, uid, "produced-audio.mp3",
				0, CommonMediaTypes.MP3);
		var episode = this.createPodcastEpisode(podcastId, title, description, image, producedGraphic, producedAudio);
		var seg = this.createPodcastEpisodeSegment(currentMogulId, episode.id(), "", 0);
		Assert.notNull(seg, "could not create a podcast episode segment for episode " + episode.id());
		return this.getPodcastEpisodeById(episode.id());
	}

	@Override
	public Episode updatePodcastEpisodeDraft(Long episodeId, String title, String description) {
		Assert.notNull(episodeId, "the episode is null");
		Assert.hasText(title, "the title is null");
		Assert.hasText(description, "the description is null");
		this.db.sql("update podcast_episode set title = ?, description =? where id = ?")
			.params(title, description, episodeId)
			.update();
		this.publisher.publishEvent(new PodcastEpisodeUpdatedEvent(this.getPodcastEpisodeById(episodeId)));
		return this.getPodcastEpisodeById(episodeId);
	}

	@Override
	public void writePodcastEpisodeProducedAudio(Long episodeId, Long managedFileId) {
		try {
			this.managedFileService.refreshManagedFile(managedFileId);
			this.db //
				.sql("update podcast_episode set produced_audio_updated=? where id=? ") //
				.params(new Date(), episodeId) //
				.update();
			this.log.debug("updated episode {} to have non-null produced_audio_updated", episodeId);
			this.publisher.publishEvent(new PodcastEpisodeUpdatedEvent(getPodcastEpisodeById(episodeId)));
			this.getPodcastEpisodeById(episodeId);
		} //
		catch (Throwable throwable) {
			throw new RuntimeException("got an exception dealing with " + throwable.getLocalizedMessage(), throwable);
		}
	}

	@Override
	public Collection<Episode> getAllPodcastEpisodesByIds(List<Long> episodeIds) {
		var episodesToSegments = this.getPodcastEpisodeSegmentsByEpisodes(episodeIds);
		var ids = episodeIds.stream().map(e -> Long.toString(e)).collect(Collectors.joining(","));
		return db.sql("select * from podcast_episode pe where pe.id in ( " + ids + " )")
			.query(new EpisodeRowMapper(managedFileService::getManagedFile, episodesToSegments::get))
			.list();
	}

	@EventListener
	void podcastDeletedEventNotifyingListener(PodcastDeletedEvent event) {
		this.log.debug("podcastDeletedEventNotifyingListener");
		var notificationEvent = NotificationEvent.notificationEventFor(event.podcast().mogulId(), event,
				Long.toString(event.podcast().id()), event.podcast().title(), false, true);
		NotificationEvents.notify(notificationEvent);
	}

	@EventListener
	void podcastCreatedEventNotifyingListener(PodcastCreatedEvent event) {
		this.log.debug("podcastCreatedEventNotifyingListener");
		var notificationEvent = NotificationEvent.notificationEventFor(event.podcast().mogulId(), event,
				Long.toString(event.podcast().id()), event.podcast().title(), false, true);
		NotificationEvents.notify(notificationEvent);
	}

	@Override
	public Collection<Podcast> getAllPodcastsByMogul(Long mogulId) {
		var pds = this.db //
			.sql("select * from podcast p where p.mogul_id = ?")//
			.param(mogulId)//
			.query(this.podcastRowMapper)//
			.list();
		return pds;
	}

}
