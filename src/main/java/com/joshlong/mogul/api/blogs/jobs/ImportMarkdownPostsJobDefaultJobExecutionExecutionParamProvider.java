package com.joshlong.mogul.api.blogs.jobs;

import com.joshlong.mogul.api.jobs.DefaultJobExecutionParamProvider;
import com.joshlong.mogul.api.jobs.Job;
import com.joshlong.mogul.api.jobs.JobExecution;
import com.joshlong.mogul.api.managedfiles.CommonMediaTypes;
import com.joshlong.mogul.api.managedfiles.ManagedFile;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Supplier;

@Component
class ImportMarkdownPostsJobDefaultJobExecutionExecutionParamProvider implements DefaultJobExecutionParamProvider {

	private final ManagedFileService managedFileService;

	ImportMarkdownPostsJobDefaultJobExecutionExecutionParamProvider(ManagedFileService managedFileService) {
		this.managedFileService = managedFileService;
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
