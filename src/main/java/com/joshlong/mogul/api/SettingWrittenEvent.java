package com.joshlong.mogul.api;

public record SettingWrittenEvent(Long mogulId, String category, String key, String value) {
}
