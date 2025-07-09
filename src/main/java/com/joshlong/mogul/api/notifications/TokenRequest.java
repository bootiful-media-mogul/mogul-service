package com.joshlong.mogul.api.notifications;

record TokenRequest(String keyName, String nonce, String mac, String timestamp, String ttl, String capability) {
}
