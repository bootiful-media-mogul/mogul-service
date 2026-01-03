package com.joshlong.mogul.api.utils;

import java.sql.ResultSet;

public interface RewindableResultSet extends ResultSet {

	void rewind();

}
