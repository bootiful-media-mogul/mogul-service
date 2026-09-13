package com.joshlong.mogul.api.mogul;

import com.joshlong.mogul.api.PublisherPlugin;

/**
 * plugins that publish on behalf of the {@link Mogul} itself, and not on behalf of some
 * entity the mogul owns (a {@code Post}, an {@code Episode}, etc.). these are the plugins
 * surfaced on the client's home page.
 */
public interface MogulPublisherPlugin extends PublisherPlugin<Mogul> {

}
