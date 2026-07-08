package com.joshlong.mogul.api.blogs.db;

import com.joshlong.mogul.api.blogs.BlogService;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
class V34__PostManagedFilesMigration extends BaseJavaMigration {

	private final ManagedFileService managedFileService;

	private final BlogService blogService;

	V34__PostManagedFilesMigration(@Lazy ManagedFileService managedFileService, @Lazy BlogService blogService) {
		this.managedFileService = managedFileService;
		this.blogService = blogService;
	}

	@Override
	public void migrate(Context context) throws Exception {
		IO.println("migrating the ManagedFile Posts");
		Assert.notNull(this.managedFileService, "the ManagedFileService reference is required");
		Assert.notNull(this.blogService, "the BlogService reference is required");
	}

}
