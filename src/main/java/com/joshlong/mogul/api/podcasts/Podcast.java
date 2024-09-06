package com.joshlong.mogul.api.podcasts;

import java.util.Date;
import java.util.List;

public record Podcast(Long mogulId, Long id, String title, Date created, List<Episode> episodes) {
}
