package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.managedfiles.CommonMediaTypes;
import com.joshlong.mogul.api.managedfiles.ManagedFile;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.managedfiles.ManagedFileUpdatedEvent;
import com.joshlong.mogul.api.mogul.MogulAuthenticatedEvent;
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

	// this will be our main cache mogul(1) => podcasts(N)
	private final Map<Long, Collection<Podcast>> podcasts = new ConcurrentHashMap<>();

	private final PodcastRowMapper podcastRowMapper;

	private final EpisodeSegmentRowMapper episodeSegmentRowMapper;

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
		this.episodeSegmentRowMapper = new EpisodeSegmentRowMapper(this.managedFileService::getManagedFile);
	}

	@Override
	public Map<Episode, List<Segment>> getEpisodeSegmentsByEpisodes(List<Episode> episodes) {
		var map = new HashMap<Episode, List<Segment>>();
		for (var collectionOfPodcasts : this.podcasts.values()) {
			for (var podcast : collectionOfPodcasts) {
				for (var episode : podcast.episodes()) {
					for (var e : episodes)
						if (episode.id().equals(e.id()))
							map.put(episode, episode.segments());
				}
			}
		}
		return map;
	}

	private List<Segment> getEpisodeSegmentsByEpisodeFromDatabase(Long episodeId) {
		this.log.debug("trying to resolve episode segments for episode #{} from the DB", episodeId);
		var sql = " select * from podcast_episode_segment where podcast_episode_id = ? order by sequence_number ASC ";
		var episodeSegmentsFromDb = this.db.sql(sql).params(episodeId).query(this.episodeSegmentRowMapper).list();
		this.log.debug("episodeSegmentsFromDb: {}", episodeSegmentsFromDb);
		return episodeSegmentsFromDb;
	}

	@Override
	public List<Segment> getEpisodeSegmentsByEpisode(Long episodeId) {
		for (var cp : this.podcasts.values())
			for (var p : cp)
				for (var e : p.episodes())
					if (e.id().equals(episodeId))
						return e.segments() != null && !e.segments().isEmpty() ? //
								e.segments() : //
								this.getEpisodeSegmentsByEpisodeFromDatabase(episodeId);

		// read through the DB.
		return this.getEpisodeSegmentsByEpisodeFromDatabase(episodeId);
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
		var mogul = getPodcastById(this.getEpisodeById(episodeId).podcastId()).mogulId();
		this.podcasts.remove(mogul);

		var episode = this.getEpisodeById(episodeId);
		var segments = this.getEpisodeSegmentsByEpisode(episodeId);

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
		var episode = this.getEpisodeById(episodeId);
		var segments = this.getEpisodeSegmentsByEpisode(episodeId);
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
		this.refreshMogulPodcasts(getPodcastById(episode.podcastId()).mogulId());
		var episodeById = this.getEpisodeById(episode.id());
		Assert.state(episodeById.complete() == complete, "the complete value in the episode in the cache "
				+ "should match the value we just wrote to the database");
		this.log.debug("the episode #{} is complete complete? {}", episode.id(), complete);
		for (var e : Set.of(new PodcastEpisodeUpdatedEvent(episodeById),
				new PodcastEpisodeCompletionEvent(episodeById)))
			this.publisher.publishEvent(e);
	}

	@ApplicationModuleListener
	void mogulCreated(MogulCreatedEvent createdEvent) {
		var podcast = this.createPodcast(createdEvent.mogul().id(), createdEvent.mogul().username() + "'s Podcast");
		Assert.notNull(podcast, "there should be a newly created podcast" + " associated with the mogul ["
				+ createdEvent.mogul() + "]");
	}

	@Override
	public Collection<Episode> getEpisodesByPodcast(Long podcastId) {
		var podcast = this.getPodcastById(podcastId);
		Assert.notNull(podcast, "the podcast with id [" + podcastId + "] is null");
		return podcast.episodes();
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
		var episode = this.getEpisodeById(episodeId); // yuck.
		this.publisher.publishEvent(new PodcastEpisodeCreatedEvent(episode));// reset
																				// cache
		return episode;
	}

	@Override
	public Episode getEpisodeById(Long episodeId) {
		this.log.debug("getEpisodeById ({})", episodeId);
		for (var collectionOfPodcasts : this.podcasts.values())
			for (var podcast : collectionOfPodcasts)
				for (var e : podcast.episodes())
					if (e.id().equals(episodeId))
						return e;
		// fine it's not in cache. let's try sql.
		var episodeByIdFromDatabase = this.getEpisodeByIdFromDatabase(episodeId);
		if (episodeByIdFromDatabase != null)
			return episodeByIdFromDatabase;

		throw new IllegalStateException("could not find the episode #" + episodeId);

	}

	/**
	 * special case handling of retrieving an episode from the SQL DB before it's been
	 * cached. we can only cache it by publishing an event which in turn takes - you
	 * guessed it - an episode. chicken and egg. so this loads everythign from the DB
	 * (just once) so that we can then invalidate the cache and then in a subsequent call
	 * use the cached version of the episode.
	 */
	private Episode getEpisodeByIdFromDatabase(Long episodeId) {
		var erm = new EpisodeRowMapper(managedFileService::getManagedFile, this::getEpisodeSegmentsByEpisode);//
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
		var segments = this.getEpisodeSegmentsByEpisode(episodeId);
		var segment = this.getEpisodeSegmentById(segmentId);
		var positionOfSegment = segments.indexOf(segment);
		var newPositionOfSegment = positionOfSegment + position;
		if (newPositionOfSegment < 0 || newPositionOfSegment > (segments.size() - 1)) {
			this.log.debug("you're trying to move out of bounds");
			return;
		}
		segments.remove(segment);
		segments.add(newPositionOfSegment, segment);
		this.reorderSegments(segments);

		var epId = this.getEpisodeSegmentById(segmentId).episodeId();
		var ep = this.getEpisodeById(epId);
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
		var segment = this.getEpisodeSegmentById(episodeSegmentId);
		Assert.state(segment != null, "you must specify a valid " + Segment.class.getName());
		var managedFilesToDelete = Set.of(segment.audio().id(), segment.producedAudio().id());
		this.db.sql("delete from podcast_episode_segment where id =?").params(episodeSegmentId).update();
		for (var managedFileId : managedFilesToDelete)
			this.managedFileService.deleteManagedFile(managedFileId);
		this.reorderSegments(this.getEpisodeSegmentsByEpisode(segment.episodeId()));
		this.refreshPodcastEpisodeCompleteness(segment.episodeId());
	}

	@Override
	public void deletePodcast(Long podcastId) {
		var podcast = this.getPodcastById(podcastId);
		for (var episode : this.getEpisodesByPodcast(podcastId)) {
			deletePodcastEpisode(episode.id());
		}
		this.db.sql("delete from podcast where id= ?").param(podcastId).update();
		this.publisher.publishEvent(new PodcastDeletedEvent(podcast));
	}

	@Override
	public void deletePodcastEpisode(Long episodeId) {
		var segmentsForEpisode = this.getEpisodeSegmentsByEpisode(episodeId);
		if (segmentsForEpisode == null)
			segmentsForEpisode = new ArrayList<>();

		var func = (BiConsumer<ManagedFile, Set<Long>>) (mf, ids) -> {
			if (mf != null)
				ids.add(mf.id());
		};

		var episode = this.getEpisodeById(episodeId);

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
		for (var allPodcasts : this.podcasts.values()) {
			for (var podcast : allPodcasts) {
				if (podcast.id().equals(podcastId))
					return podcast;
			}
		}
		return null;
	}

	@Override
	public Segment createEpisodeSegment(Long mogulId, Long episodeId, String name, long crossfade) {
		var maxOrder = (db
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
		var episodeSegmentsByEpisode = this.getEpisodeSegmentsByEpisode(episodeId);
		this.log.debug("episode segments: {}", episodeSegmentsByEpisode);
		this.reorderSegments(episodeSegmentsByEpisode);
		this.refreshPodcastEpisodeCompleteness(episodeId);
		return this.getEpisodeSegmentById(id.longValue());
	}

	@Override
	public Segment getEpisodeSegmentById(Long episodeSegmentId) {
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
		this.createEpisodeSegment(currentMogulId, episode.id(), "", 0);
		return this.getEpisodeById(episode.id());
	}

	@Override
	public Episode updatePodcastEpisodeDraft(Long episodeId, String title, String description) {
		Assert.notNull(episodeId, "the episode is null");
		Assert.hasText(title, "the title is null");
		Assert.hasText(description, "the description is null");
		this.db.sql("update podcast_episode set title = ?, description =? where id = ?")
			.params(title, description, episodeId)
			.update();
		this.publisher.publishEvent(new PodcastEpisodeUpdatedEvent(this.getEpisodeById(episodeId)));
		return this.getEpisodeById(episodeId);
	}

	@Override
	public Episode writePodcastEpisodeProducedAudio(Long episodeId, Long managedFileId) {
		try {
			this.managedFileService.refreshManagedFile(managedFileId);
			this.db //
				.sql("update podcast_episode set produced_audio_updated=? where id=? ") //
				.params(new Date(), episodeId) //
				.update();
			this.log.debug("updated episode {} to have non-null produced_audio_updated", episodeId);
			this.publisher.publishEvent(new PodcastEpisodeUpdatedEvent(getEpisodeById(episodeId)));
			return this.getEpisodeById(episodeId);
		} //
		catch (Throwable throwable) {
			throw new RuntimeException("got an exception dealing with " + throwable.getLocalizedMessage(), throwable);
		}
	}

	@EventListener
	synchronized void primeMogulPodcastCache(MogulAuthenticatedEvent mogulAuthenticatedEvent) {
		this.log.debug("primeThePodcastCache");
		var mogulId = mogulAuthenticatedEvent.mogul().id();
		if (!this.podcasts.containsKey(mogulId))
			this.refreshMogulPodcasts(mogulId);
	}

	@EventListener
	void podcastEpisodeDeletedEvent(PodcastEpisodeDeletedEvent pde) {
		this.log.debug("refreshMogulPodcasts: podcastEpisodeDeletedEvent");
		var podcastId = pde.episode().podcastId();
		var podcast = this.getPodcastById(podcastId);
		var mogul = podcast.mogulId();
		var mogulPodcasts = this.podcasts.get(mogul);
		for (var p : mogulPodcasts) {
			if (p.id().equals(podcastId)) {
				var updated = p.episodes().stream().filter(e -> !e.id().equals(pde.episode().id())).toList();
				p.episodes().clear();
				p.episodes().addAll(updated);
			}
		}
		// this.refreshMogulPodcasts(this.getPodcastById(pde.episode().podcastId()).mogulId());
	}

	@EventListener
	void podcastDeletedEvent(PodcastDeletedEvent pde) {
		this.log.debug("refreshMogulPodcasts: podcastDeletedEvent");
		this.podcasts.remove(pde.podcast().id());
	}

	@EventListener
	void podcastCreatedEvent(PodcastCreatedEvent pde) {
		this.log.debug("refreshMogulPodcasts: podcastCreatedEvent");
		this.refreshMogulPodcasts(pde.podcast().mogulId());
	}

	@EventListener
	// want this to be synchronized
	void podcastEpisodeUpdatedEvent(PodcastEpisodeUpdatedEvent peue) {
		this.log.debug("refreshMogulPodcasts: podcastEpisodeUpdatedEvent");
		this.refreshMogulPodcasts(this.getPodcastById(peue.episode().podcastId()).mogulId());
	}

	@EventListener
	void podcastDeletedEventNotifyingListener(PodcastDeletedEvent event) {
		this.log.debug("podcastDeletedEventNotifyingListener");
		var notificationEvent = NotificationEvent.notificationEventFor(event.podcast().mogulId(), event,
				Long.toString(event.podcast().id()), event.podcast().title(), false, true);
		NotificationEvents.notify(notificationEvent);
	}

	@EventListener
	void podcastEpisodeCreatedEvent(PodcastEpisodeCreatedEvent podcastEpisodeCreatedEvent) {
		this.log.debug("podcastEpisodeCreatedEvent");
		var mogulId = this.getPodcastById(podcastEpisodeCreatedEvent.episode().podcastId()).mogulId();
		this.log.debug("going to refresh the podcasts associated with mogul #{}", mogulId);
		this.refreshMogulPodcasts(mogulId);
	}

	@EventListener
	void podcastCreatedEventNotifyingListener(PodcastCreatedEvent event) {
		this.log.debug("podcastCreatedEventNotifyingListener");
		var notificationEvent = NotificationEvent.notificationEventFor(event.podcast().mogulId(), event,
				Long.toString(event.podcast().id()), event.podcast().title(), false, true);
		NotificationEvents.notify(notificationEvent);
	}

	private void refreshMogulPodcasts(Long mogulId) {
		if (this.log.isDebugEnabled()) {
			this.log.debug("resetting the entire podcast object graph for mogulId #{}", mogulId);
		}

		var podcasts = this.db//
			.sql("select * from podcast where mogul_id = ? order by created")
			.param(mogulId)
			.query(this.podcastRowMapper)
			.stream()
			.collect(Collectors.toMap(Podcast::id, p -> p));

		if (podcasts.isEmpty()) {
			log.debug("there are no podcasts associated" + " with the mogul #{}, so nothing to cache. returning.",
					mogulId);
			return;
		}

		var managedFiles = this.managedFileService.getAllManagedFilesForMogul(mogulId)
			.stream()
			.collect(Collectors.toMap(ManagedFile::id, mf -> mf));

		var segments = new HashMap<Long, List<Segment>>();
		var sql = """
					select pes.*
					from podcast p, podcast_episode pe, podcast_episode_segment pes
					where p.mogul_id = ? and p.id = pe.podcast_id and pe.id = pes.podcast_episode_id
				""";
		this.db //
			.sql(sql)
			.param(mogulId)
			.query(new EpisodeSegmentRowMapper(managedFiles::get))
			.stream()
			.forEach(seg -> segments.computeIfAbsent(seg.episodeId(), s -> new ArrayList<>()).add(seg));

		segments.values().forEach(list -> list.sort(Comparator.comparing(Segment::order)));
		var ids = this.db.sql("select id from  podcast where mogul_id = ? ")
			.param(mogulId)
			.query((rs, rowNum) -> rs.getLong("id"))
			.stream()
			.map(i -> Long.toString(i))
			.collect(Collectors.joining(","));
		this.db//
			.sql("select * from podcast_episode where podcast_id in (" + ids + ")")//
			.query(new EpisodeRowMapper(managedFiles::get, segments::get))//
			.stream()//
			.forEach(episode -> {
				var podcastId = episode.podcastId();
				podcasts.get(podcastId).episodes().add(episode);
			});

		var podcastCollection = podcasts.values();
		podcastCollection.forEach(p -> p.episodes().sort(Comparator.comparing(Episode::created).reversed()));
		this.podcasts.put(mogulId, podcastCollection);
	}

	@Override
	public Collection<Podcast> getAllPodcastsByMogul(Long mogulId) {
		return this.podcasts.get(mogulId);
	}

}
