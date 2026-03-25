package com.joshlong.mogul.api.blogs.jobs;

import com.joshlong.mogul.api.blogs.BlogService;
import com.joshlong.mogul.api.managedfiles.CommonMediaTypes;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.mogul.MogulService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

@SpringBootTest
class BlogPostImporterTest {

	@Autowired
	private BlogPostImporter blogPostImporter;

	@Test
	void importBlogPost(@Autowired MogulService mogulService, @Autowired BlogService blogService,
			@Autowired ManagedFileService managedFileService, @Autowired BlogPostImporter blogPostImporter)
			throws Exception {

		var mogul = mogulService.login("jlong", "jlong", "josh@joshlong.com", "josh", "long");
		var blog = blogService.createBlog(mogul.id(), "a simple blog", "a simple description");
		var fileName = "sample-blogs.tar.gz";
		var zip = CommonMediaTypes.TGZ;
		var archive = managedFileService.createManagedFile(mogul.id(), "sample-archives", fileName, 0, zip, false);
		var resource = new ClassPathResource("/samples/sample-blogs.tar.gz");
		Assertions.assertTrue(resource.exists(), "the blog archive does not exist");
		managedFileService.write(archive.id(), fileName, zip, resource);
		archive = managedFileService.getManagedFileById(archive.id());
		Assertions.assertTrue(archive.written(), "the blog archive is written");
		Assertions.assertEquals(archive.size(), resource.contentLength(),
				"there should be the same number of bytes written");

		blogPostImporter.importBlogPostsFromArchive(mogul.id(), blog.id(), archive.id());
	}

}