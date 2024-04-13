package com.joshlong.mogul.api.podcasts;

import java.util.Date;

/**
 *
 * represents one of many possible episode feeds, or carriers for episodes, of logically
 * distinct things. al moguls have, at the very least, one podcast automatically assigned
 * to them.
 *
 * @param mogulId
 * @param title
 */
public record Podcast(Long mogulId, Long id, String title, Date created) {
}
