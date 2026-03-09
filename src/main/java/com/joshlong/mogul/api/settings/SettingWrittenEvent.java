package com.joshlong.mogul.api.settings;

public record SettingWrittenEvent(Long mogulId, String category, String key, String value) {
}
