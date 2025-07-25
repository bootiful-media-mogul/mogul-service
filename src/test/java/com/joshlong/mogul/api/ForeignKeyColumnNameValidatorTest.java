package com.joshlong.mogul.api;

import com.joshlong.mogul.api.utils.CollectionUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.util.ArrayList;

@SpringBootTest
class ForeignKeyColumnNameValidatorTest {

	private final Logger log = LoggerFactory.getLogger(getClass());

	@Test
	void foreignKeyRunner(@Autowired DataSource db) throws Exception {
		var counterOfBadForeignKeys = 0;
		var bad = new ArrayList<String>();
		try (var conn = db.getConnection()) {
			var metaData = conn.getMetaData();
			var tables = metaData.getTables(null, null, "%", new String[] { "TABLE" });
			while (tables.next()) {
				var tableName = tables.getString("TABLE_NAME");
				var foreignKeys = metaData.getImportedKeys(null, null, tableName);
				while (foreignKeys.next()) {
					var fkColumnName = foreignKeys.getString("FKCOLUMN_NAME");
					var pkTableName = foreignKeys.getString("PKTABLE_NAME");

					// Check if the FK column name ends with "_id"
					if (!fkColumnName.toLowerCase().endsWith("_id")) {
						bad.add(String.format(
								"Table '%s' has foreign key column '%s' referencing '%s' which does NOT end with '_id'%n",
								tableName, fkColumnName, pkTableName));
						counterOfBadForeignKeys += 1;
					}
				}
				foreignKeys.close();
			}

			tables.close();

			this.log.info("found {} bad foreign keys: {}{}", counterOfBadForeignKeys, System.lineSeparator(),
					CollectionUtils.join(bad, ""));

			Assertions.assertEquals(0, counterOfBadForeignKeys,
					"there should be no foreign keys that do not end with '_id'");
		}

	}

}
