package io.kestra.core.models.ui;

import java.util.Map;

public record PluginUiModuleWithGroup(String uiModule, String group, Map<String, Object> staticInfo) {

}
