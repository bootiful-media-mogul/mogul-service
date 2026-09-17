package com.joshlong.mogul.api.jobs;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * the `job` table outlives the application that wrote it -- the test suite shares this
 * database and registers jobs of its own. a row whose {@link Job} is not in this
 * application must not be offered as something to run: it used to reach the executor as a
 * null and die there on a NullPointerException.
 */
@SpringBootTest
class PhantomJobTest {

	@Test
	void aJobRowWithNoJobBeanIsNotOffered(@Autowired JdbcClient db, @Autowired Jobs jobs) {
		var phantom = "phantomJob" + UUID.randomUUID().toString().replace("-", "");
		db.sql("insert into job(job_name) values (?)").params(phantom).update();
		try {
			var all = jobs.jobs();
			assertFalse(all.containsKey(phantom), "a job with no instance behind it should not be listed");
			for (var entry : all.entrySet())
				assertNotNull(entry.getValue(), "no listed job may have a null instance: " + entry.getKey());
		} //
		finally {
			db.sql("delete from job where job_name = ?").params(phantom).update();
		}
	}

}
