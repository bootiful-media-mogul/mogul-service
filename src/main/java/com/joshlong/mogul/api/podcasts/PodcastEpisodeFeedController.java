package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.Publication;
import com.joshlong.mogul.api.feeds.FeedTemplate;
import com.joshlong.mogul.api.feeds.SyndEntryMapper;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.publications.PublicationService;
import com.joshlong.templates.MarkdownService;
import com.rometools.rome.feed.synd.SyndContentImpl;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndLinkImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

@Controller
@ResponseBody
class PodcastEpisodeFeedController {

	private static final String PODCAST_FEED_URL = "/public/feeds/moguls/{mogulId}/podcasts/{podcastId}/episodes.atom";


	private final Logger log = LoggerFactory.getLogger(getClass());

	private final ManagedFileService managedFileService;
	private final FeedTemplate template;
	private final PodcastService podcastService;
	private final PublicationService publicationService;
	private final MarkdownService markdownService;


	PodcastEpisodeFeedController(ManagedFileService managedFileService, FeedTemplate template, PodcastService podcastService, PublicationService publicationService,
								 MarkdownService markdownService) {
		this.managedFileService = managedFileService;
		this.template = template;
		this.podcastService = podcastService;
		this.publicationService = publicationService;
		this.markdownService = markdownService;
	}

	@GetMapping(PODCAST_FEED_URL)
	ResponseEntity<String> podcastsFeed(@PathVariable long mogulId, @PathVariable long podcastId) {

		var podcast = this.podcastService.getPodcastById(podcastId);
		var episodes = this.podcastService.getPodcastEpisodesByPodcast(podcastId);
		Assert.state(podcast.mogulId().equals(mogulId), "the mogulId must match");

		// todo might want to extract this out to a generic thing as we start to expose more and more feeds across the project.
		// todo we also need to make sure we do the right thing around the global URI namespace, too.

		var params = Map.of("mogulId", mogulId, "podcastId", podcastId);
		var ns = PODCAST_FEED_URL;
		for (var k : params.keySet()) {
			var v = params.get(k);
			this.log.debug("the following parameter was found: {}={}", k, v);
			var find = "{" + k + "}";
			if (ns.contains(find)) {
				ns = ns.replace(find, v.toString());
			}
		}
		
		var title = podcast.title();
		var map = new HashMap<Long, String>();
		for (var e : episodes) {
			var publicationUrl = publicationUrl(e);
			if (StringUtils.hasText(publicationUrl)) {
				map.put(e.id(), publicationUrl);
			}
		}
		var publishedEpisodes = episodes.stream().filter(ep -> map.containsKey(ep.id())).toList();
		var syndEntryMapper = new EpisodeSyndEntryMapper(map);
		var feed = this.template.buildFeed(FeedTemplate.FeedType.ATOM_0_3, title, ns, title, publishedEpisodes, syndEntryMapper);
		var render = this.template.render(feed);
		return ResponseEntity//
			.status(HttpStatusCode.valueOf(200))//
			.contentType(MediaType.APPLICATION_ATOM_XML)//
			.body(render);
	}

	private String publicationUrl(Episode ep) {
		var publications = this.publicationService.getPublicationsByPublicationKeyAndClass(ep.publicationKey(),
				Episode.class);
		if (ep.complete() && !publications.isEmpty())
			return publications//
				.stream()//
				.sorted(((Comparator<Publication>) (o1, o2) -> {
					if (o1 != null && o2 != null) {
						if (o1.published() != null && o2.published() != null)
							return o1.published().compareTo(o2.published());
						if (o1.created() != null && o2.created() != null)
							return o1.created().compareTo(o2.created());
					}
					return 0;
				})//
						.reversed())//
				.toList()
				.getFirst()
				.url();
		return null;
	}

	private class EpisodeSyndEntryMapper implements SyndEntryMapper<Episode> {

		private final Map<Long, String> urls;

		EpisodeSyndEntryMapper(Map<Long, String> urls) {
			this.urls = urls;
		}

		@Override
		public SyndEntry map(Episode episode) throws Exception {

			var entry = new SyndEntryImpl();
			entry.setTitle(episode.title());
			entry.setLink(urls.get(episode.id()));
			entry.setPublishedDate(episode.created());

			var description = new SyndContentImpl();
			description.setType(MediaType.TEXT_PLAIN_VALUE);
			description.setValue(episode.description());

			// todo get the image

			var graphicId = episode. producedGraphic().id();
			var url = "/api"+ managedFileService.getPublicUrlForManagedFile(graphicId);
			
			//    <link rel="enclosure" type="image/jpeg" href="https://example.com/image.jpg"/>
			// claude says this is a thing so... 
			var image = new SyndLinkImpl();
			image.setHref(url);
			image.setRel("enclosure");
			image.setType(episode.graphic().contentType());
			entry.getLinks().add(image);

			entry.setDescription(description);
			return entry;

		}

		private String markdownDescription(String description) {
			try {
				return markdownService.convertMarkdownTemplateToHtml(description);
			}
			catch (Throwable throwable) {
				log.warn("couldn't transcode the following " + "string into Markdown: {}", description + "");
			}
			return description;
		}

	}

}
