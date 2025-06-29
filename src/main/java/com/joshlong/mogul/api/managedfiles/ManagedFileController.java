package com.joshlong.mogul.api.managedfiles;

import com.joshlong.mogul.api.mogul.MogulService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Controller
class ManagedFileController {

	private static final String PUBLIC_MF_URL = "/public/managedfiles/{id}";

	private static final String MF_RW_URL = "/managedfiles/{id}";

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final ManagedFileService managedFileService;

	private final MogulService mogulService;

	ManagedFileController(ManagedFileService managedFileService, MogulService mogulService) {
		this.managedFileService = managedFileService;
		this.mogulService = mogulService;
	}

	@MutationMapping
	boolean setManagedFileVisibility(@Argument Long managedFileId, @Argument boolean visible) {
		this.managedFileService.setManagedFileVisibility(managedFileId, visible);
		return true;
	}

	@SchemaMapping
	String visibleUrl(ManagedFile managedFile) {
		return this.managedFileService.getPublicUrlForManagedFile(managedFile.id());
	}

	@SchemaMapping
	String downloadableUrl(ManagedFile managedFile) {
		if (managedFile.written() && managedFile.visible()) {
			var publicUrl = this.managedFileService.getPublicUrlForManagedFile(managedFile.id());
			return publicUrl + "?download=true";
		}
		return null;
	}

	@SchemaMapping
	String url(ManagedFile managedFile) {
		return this.managedFileService.getPrivateUrlForManagedFile(managedFile.id());
	}

	@QueryMapping
	ManagedFile managedFileById(@Argument Long managedFileId) {
		return this.managedFileService.getManagedFile(managedFileId);
	}

	@ResponseBody
	@GetMapping(PUBLIC_MF_URL)
	ResponseEntity<Resource> readPublic(@PathVariable Long id) {
		return this.doRead(true, id);
	}

	@ResponseBody
	@GetMapping(MF_RW_URL)
	ResponseEntity<Resource> read(@PathVariable Long id) {
		return this.doRead(false, id);
	}

	private ResponseEntity<Resource> doRead(boolean assertVisible, Long managedFileId) {
		Assert.notNull(managedFileId, "the managedFileId is null");
		var managedFile = this.managedFileService.getManagedFile(managedFileId);
		Assert.notNull(managedFile, "the managed file does not exist [" + managedFileId + "]");
		if (assertVisible) {
			if (!managedFile.visible()) {
				this.log.warn("someone is attempting to read managed file #{} even though it's not visible publicly",
						managedFile.id());
				return ResponseEntity.notFound().build();
			}
		}
		var read = this.managedFileService.read(managedFileId);
		var contentType = managedFile.contentType();
		this.log.debug("content-type: {}", contentType);
		return ResponseEntity.ok()
			.contentLength(managedFile.size())
			.contentType(MediaType.parseMediaType(contentType))
			.body(read);
	}

	@ResponseBody
	@PostMapping(MF_RW_URL)
	Map<String, Object> write(@PathVariable Long id, @RequestParam MultipartFile file) {
		Assert.notNull(id, "the id should not be null");
		var mogul = this.mogulService.getCurrentMogul();
		var managedFile = this.managedFileService.getManagedFile(id);
		Assert.notNull(managedFile, "the managed file is null for managed file id [" + id + "]");
		Assert.state(managedFile.mogulId().equals(mogul.id()),
				"you're trying to write to an invalid file to which you are not authorized!");
		var managedFileId = managedFile.id();
		var originalFilename = file.getOriginalFilename();
		var mediaType = CommonMediaTypes.guess(file.getResource());
		log.debug("guessing the media type for [{}] is  {}", file.getOriginalFilename(), mediaType);
		this.managedFileService.write(managedFileId, originalFilename, mediaType, file.getResource());
		var updated = this.managedFileService.getManagedFile(managedFileId);
		this.log.trace("finished writing managed file [{}] to s3: {}:{}", id, originalFilename, updated.toString());
		return Map.of("managedFileId", id);
	}

}
