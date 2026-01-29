package com.joshlong.mogul.api.compositions;

import com.joshlong.mogul.api.compositions.attachments.previews.MarkdownPreview;
import com.joshlong.mogul.api.managedfiles.CommonMediaTypes;
import com.joshlong.mogul.api.managedfiles.ManagedFile;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.managedfiles.ManagedFileUpdatedEvent;
import com.joshlong.mogul.api.notifications.NotificationEvent;
import com.joshlong.mogul.api.notifications.NotificationEvents;
import com.joshlong.mogul.api.utils.CollectionUtils;
import com.joshlong.mogul.api.utils.JdbcUtils;
import com.joshlong.mogul.api.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
//todo the attachments pointing to a ManagedFile thats been cached with the wrong content-type.
// the content-type is determined asynchronously, but the CompositionService should be able to invalidate it.

/**
 * helps to manage the lifecycle and entities associated with a given composition, which
 * are blocks of text with attachments. for now, we'll assume attachments are images.
 * maybe one day we'll be able to embed, somehow, audio and videos that have been dragged
 * into a text block. it wouldn't be so hard. some sort of strategy that - given a
 * particular {@link ManagedFile}, consults the {@link ManagedFile#contentType()} and
 * helps to render Markdown that embeds a {@code <video>} player or a {@code <audio>}
 * player or an {@code <img >} tag, as appropriate. for now, though. images.
 */
@Transactional
class DefaultCompositionService implements CompositionService {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final JdbcClient db;

	private final ManagedFileService managedFileService;

	private final RowMapper<Attachment> attachmentRowMapper;

	private final Cache compositionsByKeyCache, attachmentsCache, compositionsByIdCache;

	private final ResultSetExtractor<Collection<Composition>> compositionResultSetExtractor;

	private final MarkdownPreview[] markdownPreviews;

	DefaultCompositionService(AttachmentRowMapper attachmentRowMapper, JdbcClient db, Cache compositionsByKeyCache,
			Cache compositionsByIdCache, Cache attachmentsCache, ManagedFileService managedFileService,
			MarkdownPreview[] markdownPreviews) {
		this.db = db;
		this.compositionsByIdCache = compositionsByIdCache;
		this.attachmentsCache = attachmentsCache;
		this.compositionsByKeyCache = compositionsByKeyCache;
		this.managedFileService = managedFileService;
		this.attachmentRowMapper = attachmentRowMapper;
		this.compositionResultSetExtractor = new CompositionResultSetExtractor(attachmentRowMapper, this.db);
		this.markdownPreviews = markdownPreviews;
	}

	@ApplicationModuleListener
	void onManagedFileEvent(ManagedFileUpdatedEvent event) {

		record CompositionAndAttachment(Long compositionId, Attachment attachment) {
		}

		this.log.debug("in {}, received a ManagedFileUpdatedEvent for {}", getClass().getSimpleName(),
				event.managedFile());
		var managedFileEventId = event.managedFile().id();
		var compositionAndAttachmentRowMapper = new RowMapper<CompositionAndAttachment>() {

			@Override
			public CompositionAndAttachment mapRow(ResultSet rs, int rowNum) throws SQLException {
				return new CompositionAndAttachment(rs.getLong("composition_id"),
						attachmentRowMapper.mapRow(rs, rowNum));
			}
		};
		var attachments = db //
			.sql("select * from composition_attachment where managed_file_id = ?")//
			.param(managedFileEventId) //
			.query(compositionAndAttachmentRowMapper) //
			.list();
		var compositions = new HashSet<Long>();
		for (var meta : attachments) {
			this.invalidateAttachmentCache(meta.attachment().id());
			compositions.add(meta.compositionId());
		}
		this.invalidateCompositionCacheById(compositions.iterator().next());

		NotificationEvents.notifyAsync(NotificationEvent.systemNotificationEventFor(event.managedFile().mogulId(),
				new AttachmentManagedFileUpdatedEvent(managedFileEventId), Long.toString(event.managedFile().id()),
				null));
	}

	@Override
	public Composition getCompositionById(Long id) {
		return this.getCompositionsByIds(Collections.singleton(id)).get(id);
	}

	private Composition readThroughCompositionById(Long id) {
		if (this.compositionsByIdCache.get(id) == null) {
			var comps = this.db //
				.sql("select * from composition where id = ?")//
				.param(id)//
				.query(this.compositionResultSetExtractor);
			var c = CollectionUtils.firstOrNull(comps);
			this.compositionsByIdCache.put(id, c);
			this.compositionsByKeyCache.put(compositionKey(c), c);
		}
		return this.compositionsByIdCache.get(id, Composition.class);
	}

	@Override
	public Map<Long, Composition> getCompositionsByIds(Collection<Long> ids) {
		if (ids == null || ids.isEmpty()) {
			return new HashMap<>();
		}
		var map = new HashMap<Long, Composition>();
		for (var id : ids) {
			map.put(id, this.readThroughCompositionById(id));
		}
		return map;
	}

	@Override
	public void deleteCompositionById(Long id) {
		var composition = this.readThroughCompositionById(id);
		if (composition == null) {
			return;
		}

		if (composition.attachments() != null && !composition.attachments().isEmpty())
			composition.attachments().forEach(attachment -> {
				this.deleteCompositionAttachmentyId(attachment.id());
			});
		this.db.sql("delete from composition where id = ?").param(id).update();
	}

	@Override
	public void deleteCompositionAttachmentyId(Long id) {
		// this is gross, but i need the composition_id for the caches
		var compositionId = this.db //
			.sql("select composition_id from composition_attachment where id = ?") //
			.param(id) //
			.query((rs, _) -> rs.getLong("composition_id"))//
			.single();
		var composition = this.readThroughCompositionById(compositionId);
		var attachmentById = this.readThroughAttachmentById(id);
		var mf = attachmentById.managedFile();
		this.db.sql("delete from composition_attachment where id = ?").params(id).update();
		this.invalidateAttachmentCache(id);
		this.managedFileService.deleteManagedFile(mf.id());
		this.invalidateCompositionCacheById(composition.id());
		this.invalidateCompositionCacheByKey(composition);
	}

	@Override
	public String createMarkdownPreviewForAttachment(Attachment attachment) {
		this.log.debug("creating a markdown preview for attachment {} with content-type {}", attachment,
				attachment.managedFile().contentType());
		for (var candidate : this.markdownPreviews) {
			if (candidate.supports(attachment)) {
				return candidate.preview(attachment);
			}
		}
		return null;
	}

	private Attachment readThroughAttachmentById(Long id) {
		var attachment = this.attachmentsCache.get(id, Attachment.class);
		if (attachment == null) {
			var attachments = this.db //
				.sql("select * from composition_attachment where id = ?") //
				.param(id) //
				.query(this.attachmentRowMapper)//
				.list();
			Assert.state(attachments.size() == 1, "there should be exactly one attachment for the given id " + id
					+ " but there were " + attachments.size() + " instead");
			for (var a : attachments) {
				this.attachmentsCache.put(id, a);
				attachment = a;
			}
		}
		return attachment;
	}

	private void invalidateCompositionCacheByKey(Composition composition) {
		var key = compositionKey(composition);
		this.compositionsByKeyCache.evictIfPresent(key);
	}

	private Composition readThroughCompositionByKey(Class<?> clzz, String key, String field) {
		var compositionCacheKey = this.compositionKey(clzz, key, field);
		var clazzName = clzz.getName();
		return this.compositionsByKeyCache.get(compositionCacheKey, () -> {
			this.db //
				.sql("""
						    insert into composition( payload_class,payload, field) values (?,?,?)
						    on conflict on constraint composition_payload_class_payload_field_key
						    do nothing
						""")//
				.params(clazzName, key, field) //
				.update();
			return CollectionUtils.firstOrNull(this.db //
				.sql("select * from composition where payload_class = ? and payload = ?  and field = ? ")//
				.params(clazzName, key, field)//
				.query(this.compositionResultSetExtractor));
		});
	}

	private void invalidateCompositionCacheById(Long compositionId) {
		this.compositionsByIdCache.evictIfPresent(compositionId);
	}

	private void invalidateAttachmentCache(Long attachmentId) {
		this.attachmentsCache.evictIfPresent(attachmentId);
	}

	private String compositionKey(Class<?> payloadClass, String compositionKeyAsJson, String field) {
		return payloadClass.getName() + ":" + compositionKeyAsJson + ":" + field;
	}

	private String compositionKey(Composition composition) {
		return this.compositionKey(composition.payloadClass(), composition.payload(), composition.field());
	}

	@Override
	public <T extends Composable> Composition compose(T payload, String field) {
		var clazz = payload.getClass().getName();
		var payloadKeyAsJson = JsonUtils.write(payload.compositionKey());
		var composition = this.readThroughCompositionByKey(payload.getClass(), payloadKeyAsJson, field);
		if (composition == null) {
			this.db //
				.sql("""
						  insert into composition(payload, payload_class, field) values (?,?,?)
						  on conflict on constraint composition_payload_class_payload_field_key
						  do nothing
						""")//
				.params(payloadKeyAsJson, clazz, field) //
				.update();
			composition = this.readThroughCompositionByKey(payload.getClass(), payloadKeyAsJson, field);
		}
		return composition;
	}

	@Override
	public Attachment createCompositionAttachment(Long mogul, Long compositionId, String key) {
		var managedFile = this.managedFileService.createManagedFile(mogul, "compositions", "", 0,
				CommonMediaTypes.BINARY, true);
		var gkh = new GeneratedKeyHolder();
		this.db.sql("""
				insert into composition_attachment( caption, composition_id, managed_file_id )
				values (?,?,?)
				""")//
			.params(key, compositionId, managedFile.id())//
			.update(gkh);
		var newAttachmentId = JdbcUtils.getIdFromKeyHolder(gkh).longValue();
		this.invalidateCompositionCacheById(compositionId);
		this.invalidateCompositionCacheByKey(this.readThroughCompositionById(compositionId));
		return this.readThroughAttachmentById(newAttachmentId);
	}

	public record AttachmentManagedFileUpdatedEvent(long managedFileId) {
	}

}
