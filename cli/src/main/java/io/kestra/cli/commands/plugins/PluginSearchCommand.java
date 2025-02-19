package io.kestra.cli.commands.plugins;

import io.kestra.cli.AbstractCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Command(
    name = "search",
    description = "Search for available Kestra plugins"
)
public class PluginSearchCommand extends AbstractCommand {
    private static final String API_URL = "https://api.kestra.io/v1/plugins";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private static final char SPACE = ' ';

    @Parameters(index = "0", description = "Search term (optional)", defaultValue = "")
    private String searchTerm;

    @Override
    public Integer call() throws Exception {
        super.call();

        try {
            JsonNode root = fetchPlugins();
            if (root == null) return 1;

            List<PluginInfo> plugins = findPlugins(root);
            printResults(plugins);
            return 0;

        } catch (Exception e) {
            stdOut("Error processing plugins: {0}", e.getMessage());
            return 1;
        }
    }

    private JsonNode fetchPlugins() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Accept", "application/json")
            .GET()
            .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            stdOut("API request failed with status: {0}", response.statusCode());
            return null;
        }

        return MAPPER.readTree(response.body());
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
        if (term.isEmpty()) return true;

        String name = plugin.path("name").asText().toLowerCase();
        if (name.contains(term)) return true;

        String title = plugin.path("title").asText().toLowerCase();
        if (title.contains(term)) return true;

        String group = plugin.path("group").asText().toLowerCase();
        return group.contains(term);
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
        // Pre-calculate max lengths in single pass
        int maxName = 4;
        int maxTitle = 5;
        int maxGroup = 5;

        for (PluginInfo plugin : plugins) {
            maxName = Math.max(maxName, plugin.name.length());
            maxTitle = Math.max(maxTitle, plugin.title.length());
            maxGroup = Math.max(maxGroup, plugin.group.length());
        }

        // Pre-allocate string builders for reuse
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

    private record PluginInfo(
        String name,
        String title,
        String group,
        String version
    ) {}

    @Override
    protected boolean loadExternalPlugins() {
        return false;
    }
}