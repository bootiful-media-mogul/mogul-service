package com.joshlong.mogul.api.publications;

import com.joshlong.mogul.api.Publication;
import com.joshlong.mogul.api.Publishable;
import com.joshlong.mogul.api.PublisherPlugin;

import java.util.Collection;
import java.util.Map;

/**
 * handles preparing context, launching the {@link PublisherPlugin}, and noting the
 * publication in the DB.
 */
public interface PublicationService {

	String MOGUL_ID = "mogulId";

	String PUBLICATION_ID = "publicationId";

	/**
	 * the absolute base URL of the client/studio (e.g. {@code https://host}), captured
	 * from the gateway-supplied request header so plugins can build absolute links back
	 * into the app (e.g. to a newly created blog post). present in a plugin's
	 * {@code PublishContext.context()} only when the header was available.
	 */
	String BASE_URL = "baseUrl";

	/**
	 * the HTTP header the gateway stamps on requests with the client's absolute base URL.
	 */
	String BASE_URL_HEADER = "X-Mogul-Base-Url";

	<T extends Publishable> T resolvePublishable(Long mogulId, Long id, String clazz);

	<T extends Publishable> T resolvePublishable(Long mogulId, Long id, Class<T> clazz);

	Publication getPublicationById(Long id);

	Map<Long, Publication> getPublicationsByIds(Collection<Long> ids);

	Collection<Publication> getPublicationsByPublicationKeyAndClass(Long publicationKey, Class<?> clazz);

	Collection<Publication> getPublicationsByPublicationKeyAndClass(Long publicationKey, String clazz);

	<T extends Publishable> Publication publish(Long mogulId, T payload, Map<String, String> contextAndSettings,
			PublisherPlugin<T> plugin);

	<T extends Publishable> Publication unpublish(Long mogulId, Publication publication, PublisherPlugin<T> plugin);

}
