package io.kestra.core.docs;

import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.plugin.core.dashboard.data.Flows;
import io.kestra.plugin.core.storage.Delete;

import static org.assertj.core.api.Assertions.assertThat;

class PluginTest {

    @Test
    void titleForFallsBackToThePluginsOwnTitleWhenTheClassIsNotInARegisteredSubGroup() {
        // Real-world equivalent: io.kestra.plugin.mongodb.Trigger and
        // io.kestra.plugin.debezium.mongodb.Trigger are both conventionally named `Trigger` and both
        // live in a package ending in "mongodb" — a package-name-derived guess would collide the two.
        // Each plugin's own declared title (from its metadata/index.yaml, surfaced via
        // RegisteredPlugin#title) must disambiguate them regardless of package shape.
        RegisteredPlugin mongodb = pluginWithTitle("MongoDB");
        RegisteredPlugin debeziumMongodb = pluginWithTitle("Debezium MongoDB");

        // The concrete class only matters for its package here; it isn't a real trigger of either
        // plugin, it just stands in for "some class that isn't part of a declared subgroup".
        String mongodbTitle = Plugin.titleFor(mongodb, Flow.class);
        String debeziumMongodbTitle = Plugin.titleFor(debeziumMongodb, Flow.class);

        assertThat(mongodbTitle).isEqualTo("MongoDB");
        assertThat(debeziumMongodbTitle).isEqualTo("Debezium MongoDB");
        assertThat(mongodbTitle).isNotEqualTo(debeziumMongodbTitle);
    }

    @Test
    void titleForQualifiesThePluginTitleWithTheSubGroupsOwnDeclaredTitle() {
        RegisteredPlugin core = pluginWithTitleAndGroup("core", "io.kestra.plugin.core");

        // io.kestra.plugin.core.dashboard.data declares @PluginSubGroup(title = "Data Filter", ...).
        // A sub-group title is written relative to its plugin ("SQS" under the AWS plugin), so it
        // only reads correctly behind it.
        assertThat(Plugin.titleFor(core, Flows.class)).isEqualTo("core Data Filter");
    }

    @Test
    void titleForFallsBackToTheCapitalizedSubGroupSegmentsWhenTheSubGroupDeclaresNoTitle() {
        RegisteredPlugin core = pluginWithTitleAndGroup("core", "io.kestra.plugin.core");

        // io.kestra.plugin.core.storage declares @PluginSubGroup(categories = CORE) with no title.
        assertThat(Plugin.titleFor(core, Delete.class)).isEqualTo("core Storage");
    }

    @Test
    void titleForIgnoresSubGroupsOfAClassThatLivesOutsideThePluginsGroup() {
        // EE plugins ship classes under io.kestra.plugin.core.*, so a plugin's own title must win
        // over the sub-group annotation of a package it does not own.
        RegisteredPlugin plugin = pluginWithTitleAndGroup("Kestra EE", "io.kestra.plugin.ee");

        assertThat(Plugin.titleFor(plugin, Delete.class)).isEqualTo("Kestra EE");
    }

    private static RegisteredPlugin pluginWithTitle(String title) {
        return pluginWithTitleAndGroup(title, null);
    }

    private static RegisteredPlugin pluginWithTitleAndGroup(String title, String group) {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("X-Kestra-Title", title);
        if (group != null) {
            manifest.getMainAttributes().putValue("X-Kestra-Group", group);
        }

        return RegisteredPlugin.builder().manifest(manifest).build();
    }
}
