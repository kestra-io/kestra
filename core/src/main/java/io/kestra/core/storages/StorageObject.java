package io.kestra.core.storages;

import java.io.InputStream;
import java.util.Map;

public record StorageObject(Map<String, String> metadata, InputStream inputStream) {
}
