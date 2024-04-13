package com.joshlong.mogul.api.publications;

import java.io.Serializable;

/**
 * gives us a key we can persist (probably as encrypted text..) in a serialized fashion in
 * a SQL DB.
 */
public interface Publishable {

	Serializable publicationKey();

}
