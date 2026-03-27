package com.joshlong.mogul.api.blogs.jobs;

import com.joshlong.mogul.api.archives.ArchiveExtractor;
import com.joshlong.mogul.api.archives.ArchiveFile;
import com.joshlong.mogul.api.archives.Consumers;
import com.joshlong.mogul.api.blogs.BlogService;
import com.joshlong.mogul.api.managedfiles.CommonMediaTypes;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.BufferedInputStream;
import java.sql.Date;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

@Component
class BlogPostImporter {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final ManagedFileService managedFileService;

	private final BlogService blogService;

	private final ArchiveExtractor archiveExtractor;

	private final MarkdownDocuments markdownDocuments;

	private final int availableProcessors = Runtime.getRuntime().availableProcessors();

	private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

	BlogPostImporter(ManagedFileService managedFileService, BlogService blogService, ArchiveExtractor archiveExtractor,
			MarkdownDocuments markdownDocuments) {
		this.managedFileService = managedFileService;
		this.markdownDocuments = markdownDocuments;
		this.blogService = blogService;
		this.archiveExtractor = archiveExtractor;
	}

	void importBlogPostsFromArchive(long mogulId, long blogId, long managedFileId) throws Exception {
		var archive = this.managedFileService.getManagedFileById(managedFileId);
		Assert.notNull(archive, "managed file id " + managedFileId + " not found");
		var blog = this.blogService.getBlogById(blogId);
		Assert.notNull(blog, "blog id " + blogId + " not found");
		var resourceForArchive = this.managedFileService.read(managedFileId);
		try (var bufferedInputStream = new BufferedInputStream(resourceForArchive.getInputStream())) {
			var mediaType = CommonMediaTypes.guess(bufferedInputStream);
			Assert.state(CommonMediaTypes.isArchive(mediaType), "archive is invalid");
			var files = new ArrayList<ArchiveFile>();
			this.archiveExtractor.extract(bufferedInputStream, Consumers.readableTextOnly(files::add));
			var cdl = new CountDownLatch(files.size());

			var semaphore = new Semaphore(availableProcessors);
			log.info("there are {} files in the archive to ingest.", files.size());
			for (var it : files) {
				semaphore.acquire();
				this.executor.execute(() -> {
					try {
						this.ingest(mogulId, blogId, it);
					} //
					catch (Throwable throwable) {
						this.log.error("failed to ingest blog post for blog id {} and mogul id {}", blogId, mogulId,
								throwable);
					}
					finally {
						semaphore.release();
						cdl.countDown();
					}
				});
			}
			cdl.await();
			this.log.info("finished importing blog posts for blog" + " id {} and mogul id {}", blogId, mogulId);
		}
	}

	private void ingest(long mogulId, long blogId, ArchiveFile archiveFile) {
		this.log.info("ingesting {}/{} for {}", mogulId, blogId, archiveFile);
		var contents = new String(archiveFile.content());
		var markdownDocument = this.markdownDocuments.parse(contents);

		if (this.log.isDebugEnabled())
			this.debug(markdownDocument);

		var published = markdownDocument.header().publishedAt();
		var post = this.blogService.createPost( //
				blogId, //
				Date.from(published.atStartOfDay(ZoneId.systemDefault()).toInstant()), //
				this.trim(markdownDocument.header().title()), //
				this.trim(markdownDocument.body()), //
				null //
		);
		Assert.notNull(post, "post could not be created");
	}

	private void debug(MarkdownDocument markdownDocument) {
		var thickLine = "=========";
		var smallLine = "---------";
		var content = new StringBuilder();
		var nl = System.lineSeparator();
		content.append(nl).append(thickLine).append(nl);
		markdownDocument.header().rawHeader().forEach((k, v) -> content.append('\t').append(k).append('=').append(v));
		content.append(nl).append(smallLine).append(nl);
		content.append(markdownDocument.body());
		this.log.info(content.toString());
	}

	private String trim(String input) {
		return input == null ? "" : input.trim();
	}

}
