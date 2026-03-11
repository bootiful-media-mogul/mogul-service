package com.joshlong.mogul.api.blogs.jobs;

import com.joshlong.mogul.api.archives.ArchiveExtractor;
import com.joshlong.mogul.api.archives.Tgz;
import com.joshlong.mogul.api.archives.Zip;
import com.joshlong.mogul.api.blogs.BlogService;
import com.joshlong.mogul.api.jobs.Job;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;

@Component
class ImportMarkdownPostsJob implements Job {

	private final BlogService blogService;

	private final ManagedFileService managedFileService;

	private final ArchiveExtractor zip;

	private final ArchiveExtractor tgz;

	ImportMarkdownPostsJob(@Zip ArchiveExtractor zip, @Tgz ArchiveExtractor tgz, BlogService blogService,
			ManagedFileService managedFileService) {
		this.blogService = blogService;
		this.managedFileService = managedFileService;
		this.zip = zip;
		this.tgz = tgz;
	}

	@Override
	public Result run(Map<String, Object> context) throws Exception {
		return null;
	}

	static class FrontMatter {

		private static Map<String, Object> parse(String content) {
			if (!content.startsWith("---"))
				return Map.of();

			var end = content.indexOf("---", 3);
			if (end == -1)
				return Map.of();

			var yaml = content.substring(3, end).trim();
			return new Yaml().load(yaml);
		}

		private static String body(String content) {
			if (!content.startsWith("---"))
				return content;
			var end = content.indexOf("---", 3);
			return end == -1 ? content : content.substring(end + 3).trim();
		}

	}

}
