package io.kestra.cli.commands.plugins;

import io.kestra.cli.AbstractCommand;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Command(
    name = "search",
    description = "Search for available Kestra plugins"
)
public class PluginSearchCommand extends AbstractCommand {
    @Inject
    @Client("api")
    private HttpClient httpClient;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final char SPACE = ' ';

    @Parameters(index = "0", description = "Search term (optional)", defaultValue = "")
    private String searchTerm;

    @Override
    public Integer call() throws Exception {
        super.call();

        try {
            JsonNode root = fetchPlugins();
            List<PluginInfo> plugins = findPlugins(root);
            printResults(plugins);
            return 0;
        } catch (Exception e) {
            stdOut("Error processing plugins: {0}", e.getMessage());
            return 1;
        }
    }

    private JsonNode fetchPlugins() throws Exception {
        String response = httpClient.toBlocking()
            .retrieve(
                HttpRequest.GET("/v1/plugins")
                    .header("Accept", "application/json")
            );
        return MAPPER.readTree(response);
    }

    private List<PluginInfo> findPlugins(JsonNode root) {
        String searchTermLower = searchTerm.toLowerCase();
        List<PluginInfo> plugins = new ArrayList<>();

        for (JsonNode plugin : root) {
            if (matchesSearch(plugin, searchTermLower)) {
                plugins.add(new PluginInfo(
                    plugin.path("name").asText(),
                    plugin.path("title").asText(),
                    plugin.path("group").asText(),
                    plugin.path("version").asText("")
                ));
            }
        }

        plugins.sort((p1, p2) -> p1.name.compareToIgnoreCase(p2.name));
        return plugins;
    }

    private boolean matchesSearch(JsonNode plugin, String term) {
        if (term.isEmpty()) {
            return true;
        }

        return plugin.path("name").asText().toLowerCase().contains(term) ||
            plugin.path("title").asText().toLowerCase().contains(term) ||
            plugin.path("group").asText().toLowerCase().contains(term);
    }

    private void printResults(List<PluginInfo> plugins) {
        if (plugins.isEmpty()) {
            stdOut("No plugins found{0}",
                searchTerm.isEmpty() ? "" : " matching '" + searchTerm + "'");
            return;
        }

        stdOut("\nFound {0} plugins{1}",
            plugins.size(),
            searchTerm.isEmpty() ? "" : " matching '" + searchTerm + "'"
        );

        printPluginsTable(plugins);
    }

    private void printPluginsTable(List<PluginInfo> plugins) {
        int maxName = 4, maxTitle = 5, maxGroup = 5;
        for (PluginInfo plugin : plugins) {
            maxName = Math.max(maxName, plugin.name.length());
            maxTitle = Math.max(maxTitle, plugin.title.length());
            maxGroup = Math.max(maxGroup, plugin.group.length());
        }

        StringBuilder namePad = new StringBuilder(maxName);
        StringBuilder titlePad = new StringBuilder(maxTitle);
        StringBuilder groupPad = new StringBuilder(maxGroup);

        stdOut("");
        printRow(namePad, titlePad, groupPad, "NAME", "TITLE", "GROUP", "VERSION",
            maxName, maxTitle, maxGroup);

        for (PluginInfo plugin : plugins) {
            printRow(namePad, titlePad, groupPad, plugin.name, plugin.title, plugin.group, plugin.version,
                maxName, maxTitle, maxGroup);
        }
        stdOut("");
    }

    private void printRow(StringBuilder namePad, StringBuilder titlePad, StringBuilder groupPad,
                          String name, String title, String group, String version,
                          int maxName, int maxTitle, int maxGroup) {
        stdOut("{0}  {1}  {2}  {3}",
            pad(namePad, name, maxName),
            pad(titlePad, title, maxTitle),
            pad(groupPad, group, maxGroup),
            version
        );
    }

    private String pad(StringBuilder sb, String str, int length) {
        sb.setLength(0);
        sb.append(str);
        while (sb.length() < length) {
            sb.append(SPACE);
        }
        return sb.toString();
    }

    private record PluginInfo(String name, String title, String group, String version) {}

    @Override
    protected boolean loadExternalPlugins() {
        return false;
    }
}