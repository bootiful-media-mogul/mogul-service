package com.joshlong.mogul.api.blogs.jobs;

import com.joshlong.mogul.api.jobs.*;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
class ImportMarkdownPostsJob implements Job {

	private final BlogPostImporter importer;

	ImportMarkdownPostsJob(BlogPostImporter importer) {
		this.importer = importer;
	}

	@Override
	public @NonNull Set<String> requiredContextAttributes() {
		var attrs = Job.super.requiredContextAttributes();
		var all = new HashSet<>(attrs);
		all.add(Job.BLOG_ID_KEY);
		all.add(Job.MANAGED_FILE_ID_KEY);
		return all;
	}

	@Override
	public JobExecutionResult run(JobExecutionContext context) throws Exception {
		var mogul = context.mogulId();
		var blog = context.getContextAttributeAsLong(Job.BLOG_ID_KEY);
		var managedFile = context.getContextAttributeAsLong(Job.MANAGED_FILE_ID_KEY);
		this.importer.importBlogPostsFromArchive(mogul, blog, managedFile);
		return JobExecutionResult.ok();
	}

}
