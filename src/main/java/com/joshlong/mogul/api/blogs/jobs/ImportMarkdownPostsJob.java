package com.joshlong.mogul.api.blogs.jobs;

import com.joshlong.mogul.api.blogs.BlogService;
import com.joshlong.mogul.api.jobs.*;
import com.joshlong.mogul.api.managedfiles.CommonMediaTypes;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@Component
class ImportMarkdownPostsJob implements Job, JobExecutionParamProvider {

	private final BlogService blogService;

	private final ManagedFileService managedFileService;

	private final BlogPostImporter importer;

	@Override
	public @NonNull Set<String> requiredContextAttributes() {
		var attrs = Job.super.requiredContextAttributes();
		var all = new HashSet<>(attrs);
		all.add(Job.BLOG_ID_KEY);
		all.add(Job.MANAGED_FILE_ID_KEY);
		return all;
	}

	ImportMarkdownPostsJob(BlogService blogService, ManagedFileService managedFileService, BlogPostImporter importer) {
		this.blogService = blogService;
		this.managedFileService = managedFileService;
		this.importer = importer;
	}

	@Override
	public JobExecutionResult run(JobExecutionContext context) throws Exception {
		var mogul = context.mogulId();
		var blog = context.getContextAttributeAsLong(Job.BLOG_ID_KEY);
		var managedFile = context.getContextAttributeAsLong(Job.MANAGED_FILE_ID_KEY);
		this.importer.importBlogPostsFromArchive(mogul, blog, managedFile);
		return JobExecutionResult.ok();
	}

	@Override
	public boolean supports(Job job) {
		return job instanceof ImportMarkdownPostsJob;
	}

	@Override
	public Map<String, Supplier<Object>> prepare(JobExecution jobExecution) {
		return Map.of("managedFileId", () -> {
			var managedFile = this.managedFileService.createManagedFile(jobExecution.mogulId(),
					jobExecution.jobName() + "/" + jobExecution.id(), "archive.zip", 0, CommonMediaTypes.BINARY, false);
			return managedFile.id();
		});
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
