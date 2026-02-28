package com.joshlong.mogul.api.blogs;

import java.util.Date;

public record Blog(Long mogulId, Long id, String title, String description, Date created) {
}
