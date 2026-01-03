package com.joshlong.mogul.api.utils.jdbc;

import java.sql.ResultSet;

public interface ReplayableResultSet extends ResultSet {

	void replay();

}
