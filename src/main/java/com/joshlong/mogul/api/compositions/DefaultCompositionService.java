package com.joshlong.mogul.api.compositions;

import com.joshlong.mogul.api.managedfiles.CommonMediaTypes;
import com.joshlong.mogul.api.managedfiles.ManagedFile;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.utils.JdbcUtils;
import com.joshlong.mogul.api.utils.JsonUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

/**
 * helps to manage the lifecycle and entities associated with a given composition, which
 * are blocks of text with attachments. for now, we'll assume attachments are images.
 * maybe one day we'll be able to embed, somehow, audio and videos that have been dragged
 * into a text block. it wouldn't be so hard. some sort of strategy that - given a
 * particular {@link com.joshlong.mogul.api.managedfiles.ManagedFile}, consults the
 * {@link ManagedFile#contentType()} and helps to render Markdown that embeds a
 * {@code <video>} player or a {@code <audio>} player or an {@code <img >} tag, as
 * appropriate. for now, though. images.
 */
@Service
@Transactional
class DefaultCompositionService implements CompositionService {

	private final JdbcClient db;

	private final ManagedFileService managedFileService;

	private final RowMapper<Composition> compositionRowMapper;

	private final RowMapper<Attachment> attachmentRowMapper;

	DefaultCompositionService(AttachmentRowMapper attachmentRowMapper, JdbcClient db,
			ManagedFileService managedFileService) {
		this.db = db;
		this.managedFileService = managedFileService;
		this.attachmentRowMapper = attachmentRowMapper;
		this.compositionRowMapper = new CompositionRowMapper(this::getAttachmentsByComposition);
	}

	@Override
	public Composition getCompositionById(Long id) {
		return this.db //
			.sql("select * from composition where id = ? ") //
			.params(id)//
			.query(this.compositionRowMapper) //
			.single();
	}

	@Override
	public void deleteCompositionAttachment(Long id) {
		var attachmentById = this.getAttachmentById(id);
		var mf = attachmentById.managedFile();
		this.db.sql("delete from composition_attachment where id = ?").params(id).update();
		this.managedFileService.deleteManagedFile(mf.id());
	}

	@Override
	public <T extends Composable> Composition compose(T payload, String field) {
		var generatedKeyHolder = new GeneratedKeyHolder();
		var key = JsonUtils.write(payload.compositionKey());
		var clazz = payload.getClass().getName();
		this.db //
			.sql("""
					insert into composition(payload, payload_class, field) values (?,?,?)
					on conflict on constraint composition_payload_class_payload_field_key
					do nothing
					""")//
			.params(key, clazz, field)//
			.update(generatedKeyHolder);

		return this.db //
			.sql("select * from composition where  payload_class  = ? and payload = ?  and field = ? ")//
			.params(clazz, key, field)//
			.query(this.compositionRowMapper)//
			.single();
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
		var ai = JdbcUtils.getIdFromKeyHolder(gkh).longValue();
		return this.getAttachmentById(ai);
	}

	private Attachment getAttachmentById(Long id) {
		return this.db.sql("select * from composition_attachment where id = ?")
			.params(id)
			.query(this.attachmentRowMapper)
			.single();
	}

	@Override
	public Collection<Attachment> getAttachmentsByComposition(Long compositionId) {
		return this.db//
			.sql("select * from composition_attachment where composition_id = ?") //
			.params(compositionId)//
			.query(this.attachmentRowMapper)//
			.list();
	}

}
