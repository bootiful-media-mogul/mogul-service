package com.joshlong.mogul.api.blogs.jobs;

import com.joshlong.mogul.api.archives.ArchiveExtractor;
import com.joshlong.mogul.api.archives.Tgz;
import com.joshlong.mogul.api.archives.Zip;
import com.joshlong.mogul.api.blogs.BlogService;
import com.joshlong.mogul.api.jobs.Job;
import com.joshlong.mogul.api.jobs.JobExecutionResult;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
class ImportMarkdownPostsJob implements Job {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final BlogService blogService;

	private final ManagedFileService managedFileService;

	private final ArchiveExtractor zip;

	private final ArchiveExtractor tgz;

	@Override
	public @NonNull Set<String> requiredContextAttributes() {
		var attrs = Job.super.requiredContextAttributes();
		var all = new HashSet<>(attrs);
		all.add(Job.BLOG_ID_KEY);
		return all;
	}

	ImportMarkdownPostsJob(@Zip ArchiveExtractor zip, @Tgz ArchiveExtractor tgz, BlogService blogService,
			ManagedFileService managedFileService) {
		this.blogService = blogService;
		this.managedFileService = managedFileService;
		this.zip = zip;
		this.tgz = tgz;
	}

	@Override
	public JobExecutionResult run(Map<String, Object> context) throws Exception {
		var msg = new StringBuilder();
		msg.append(String.format("running %s", getClass().getName()));
		context.forEach((k, v) -> msg.append(String.format(" context %s=%s\n", k, v)));
		this.log.info(msg.toString());

		// todo:
		// deduce the content type
		// run the appropriate ArchiveExtractor
		// extract / create posts for each file
		return JobExecutionResult.ok(context);
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
