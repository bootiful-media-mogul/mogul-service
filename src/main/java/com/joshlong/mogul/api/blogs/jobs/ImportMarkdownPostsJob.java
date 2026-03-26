package com.joshlong.mogul.api.blogs.jobs;

import com.joshlong.mogul.api.jobs.*;
import com.joshlong.mogul.api.managedfiles.CommonMediaTypes;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@Component
class ImportMarkdownPostsJob implements Job, JobExecutionParamProvider {

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

	ImportMarkdownPostsJob(ManagedFileService managedFileService, BlogPostImporter importer) {
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

}
