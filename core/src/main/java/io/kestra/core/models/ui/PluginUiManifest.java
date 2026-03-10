package io.kestra.core.models.ui;

import java.util.List;
import java.util.Map;

public record PluginUiManifest(Map<String, List<PluginUiModuleWithGroup>> manifest) {

}
