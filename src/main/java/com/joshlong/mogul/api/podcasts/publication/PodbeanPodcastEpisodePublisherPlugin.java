package com.joshlong.mogul.api.podcasts.publication;

import com.joshlong.mogul.api.managedfiles.CommonMediaTypes;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.podcasts.Episode;
import com.joshlong.mogul.api.utils.FileUtils;
import com.joshlong.podbean.EpisodeStatus;
import com.joshlong.podbean.EpisodeType;
import com.joshlong.podbean.PodbeanClient;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component(PodbeanPodcastEpisodePublisherPlugin.PLUGIN_NAME)
class PodbeanPodcastEpisodePublisherPlugin implements PodcastEpisodePublisherPlugin, BeanNameAware {

	/**
	 * well-known values written to the context after publication.
	 */
	public static final String CONTEXT_PODBEAN_PODCAST_ID = "podbeanPodcastId";

	public static final String CONTEXT_PODBEAN_EPISODE_ID = "podbeanEpisodeId";

	public static final String CONTEXT_PODBEAN_EPISODE_PUBLISH_DATE_IN_MILLISECONDS = "contextPodbeanEpisodePublishDateInMilliseconds";

	public static final String PLUGIN_NAME = "podbean";

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final AtomicReference<String> beanName = new AtomicReference<>();

	private final ManagedFileService managedFileService;

	private final PodbeanClient podbeanClient;

	PodbeanPodcastEpisodePublisherPlugin(ManagedFileService managedFileService, PodbeanClient podbeanClient) {
		this.managedFileService = managedFileService;
		this.podbeanClient = podbeanClient;
	}

	@Override
	public String name() {
		return this.beanName.get();
	}

	@Override
	public void setBeanName(@NonNull String name) {
		this.beanName.set(name);
	}

	@Override
	public boolean canPublish(PublishContext<Episode> pc) {
		return this.isConfigurationValid(pc.context()) && pc.payload() != null && pc.payload().complete();
	}

	@Override
	public Set<String> requiredSettingKeys() {
		return Set.of("clientId", "clientSecret");
	}

	@Override
	public void publish(PublishContext<Episode> pc) {
		var context = pc.context();
		var payload = pc.payload();
		this.log.debug("publishing to podbean with context [{}] and payload [{}]. produced audio is [{}]", context,
				payload, payload.producedAudio());

		var tempProducedAudioFile = this.download(this.managedFileService.read(payload.producedAudio().id()),
				FileUtils.tempFileWithExtension("mp3"));
		this.log.debug("downloaded the produced audio file for the podcast episode {}", payload.id());
		var tempGraphicFile = this.download(this.managedFileService.read(payload.producedGraphic().id()),
				FileUtils.tempFileWithExtension("jpg"));
		this.log.debug("downloaded the produced graphic for the episode {}", payload.id());

		var producedAudioAuthorization = this.podbeanClient.upload(CommonMediaTypes.MP3, tempProducedAudioFile);
		this.log.debug("got the podcast audio authorization from podbean: {}", producedAudioAuthorization);
		var producedGraphicAuthorization = this.podbeanClient.upload(CommonMediaTypes.JPG, tempGraphicFile);
		this.log.debug("got the podcast graphic authorization from podbean: {}", producedGraphicAuthorization);

		var pluginName = PLUGIN_NAME;
		var podbeanEpisode = (com.joshlong.podbean.Episode) null;
		var errorMessage = "";
		try {
			podbeanEpisode = this.podbeanClient.publishEpisode(payload.title(), payload.description(),
					EpisodeStatus.PUBLISH, EpisodeType.PUBLIC, producedAudioAuthorization.getFileKey(),
					producedGraphicAuthorization.getFileKey());
			this.log.debug("published the episode to podbean {}", podbeanEpisode.toString());
			var permalinkUrl = podbeanEpisode.getPermalinkUrl();
			if (permalinkUrl != null) {
				pc.success(pluginName, permalinkUrl);
				context.put(CONTEXT_PODBEAN_PODCAST_ID, podbeanEpisode.getPodcastId());
				context.put(CONTEXT_PODBEAN_EPISODE_ID, podbeanEpisode.getId());
				context.put(CONTEXT_PODBEAN_EPISODE_PUBLISH_DATE_IN_MILLISECONDS,
						Long.toString(podbeanEpisode.getPublishTime().getTime()));
				return;
			} //
			errorMessage = "the published episode's (Episode#%s) podbean URL is null".formatted(payload.id());
		} //
		catch (Throwable throwable) {
			errorMessage = throwable.getMessage();

		}
		pc.failure(pluginName, errorMessage);
	}

	private File download(Resource resource, File file) {
		Assert.notNull(resource, "the resource you wanted to" + " download to local file [" + file.getAbsolutePath()
				+ "] does not exist");
		try (var bin = resource.getInputStream(); var bout = new FileOutputStream(file)) {
			FileCopyUtils.copy(bin, bout);
		} //
		catch (Throwable throwable) {
			this.log.error("could not download the resource {} to file {} ", resource.getFilename(),
					file.getAbsolutePath());
			throw new RuntimeException("could not download a resource ", throwable);
		}
		return file;
	}

	@Override
	public boolean unpublish(UnpublishContext<Episode> uc) {
		var context = uc.context();
		var publication = uc.publication();
		var done = new AtomicBoolean(false);
		var episodeId = context.get(CONTEXT_PODBEAN_EPISODE_ID);
		this.podbeanClient//
			.getAllEpisodes()
			.stream()
			.filter(episode -> episode.getId().equalsIgnoreCase(episodeId))
			.forEach(episode -> {
				this.podbeanClient.updateEpisode(episodeId, episode.getTitle(), episode.getContent(),
						EpisodeStatus.DRAFT, EpisodeType.PUBLIC, null, null);
				done.set(true);
				this.log.debug("unpublishing podbean publication for episode #{}", publication.payload());
			});
		return done.get();
	}

}
