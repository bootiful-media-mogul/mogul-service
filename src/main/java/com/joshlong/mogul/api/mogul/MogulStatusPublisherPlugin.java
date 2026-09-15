package com.joshlong.mogul.api.mogul;

import com.joshlong.mogul.api.PublisherPlugin;

/**
 * plugins that publish a {@link Mogul mogul's} own day -- what they're saying right now
 * -- and not some entity the mogul owns (a {@code Post}, an {@code Episode}, etc.). these
 * are the plugins surfaced on the client's home page.
 */
public interface MogulStatusPublisherPlugin extends PublisherPlugin<MogulStatus> {

}
