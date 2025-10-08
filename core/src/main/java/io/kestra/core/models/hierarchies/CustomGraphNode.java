package io.kestra.core.models.hierarchies;

import io.kestra.core.models.Plugin;

public class CustomGraphNode extends AbstractGraph {
    private final String label;
    private final Plugin plugin;

    public CustomGraphNode(String uid, String label, Plugin plugin) {
        super(uid);

        this.label = label;
        this.plugin = plugin;
    }

    @Override
    public String getLabel() {
        return label;
    }

    public Plugin getPlugin() {
        return plugin;
    }
}
