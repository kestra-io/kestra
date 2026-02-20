package io.kestra.core.models.ui;

import java.util.List;
import java.util.Map;

public record PluginUiModule(String uiModule, Map<String, Object> staticInfo, List<String> styles) {

}
