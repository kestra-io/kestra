package io.kestra.core.models.ui;

import java.util.List;
import java.util.Map;

public record PluginUiModuleWithGroup(String uiModule, String group, Map<String, Object> staticInfo, List<String> styles) {

}
