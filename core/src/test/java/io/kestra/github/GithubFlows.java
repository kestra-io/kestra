package io.kestra.github;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.utils.TestsUtils;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;

/**
 * Prepares the flows committed in {@code .github/kestra} to run in a test. They are used as written, except for what a
 * test has to control: the GitHub API and the secrets point at {@link FakeGitHub}, the trigger is replaced by an
 * {@code event} input that carries the webhook payload, and retries wait a few milliseconds instead of seconds.
 */
final class GithubFlows {
    static final String NAMESPACE = "community.github";

    private GithubFlows() {
    }

    static String source(String name) throws IOException {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null && !Files.isDirectory(directory.resolve(".github/kestra"))) {
            directory = directory.getParent();
        }
        if (directory == null) {
            throw new IllegalStateException("Cannot find the .github/kestra directory above the working directory.");
        }
        return Files.readString(directory.resolve(".github/kestra/" + name + ".yaml"));
    }

    /**
     * @param withEvent whether the flow reads a webhook payload, which then becomes the {@code event} input
     */
    static String forTest(String name, String id, FakeGitHub github, boolean withEvent, UnaryOperator<String> tweak) throws IOException {
        String yaml = source(name)
            .replace("https://api.github.com", github.url())
            .replace("{{ secret('GITHUB_TOKEN') }}", FakeGitHub.TOKEN)
            .replace("{{ secret('MAINTAINERS_SLACK_WEBHOOK_URL') }}", github.url() + "/slack")
            .replace("interval: PT2S", "interval: PT0.01S")
            .replace("maxInterval: PT30S", "maxInterval: PT0.05S")
            .replace("interval: PT10S", "interval: PT0.01S")
            .replace("maxInterval: PT2M", "maxInterval: PT0.05S")
            .replaceFirst("(?m)^id: .*$", "id: " + id);
        String trigger = "(?ms)^triggers:\\n.*?(?=^tasks:)";
        yaml = withEvent
            ? yaml.replaceFirst(trigger, "inputs:\n  - id: event\n    type: JSON\n\n").replace("trigger.body", "inputs.event")
            : yaml.replaceFirst(trigger, "");
        return tweak.apply(yaml);
    }

    static void register(LocalFlowRepositoryLoader loader, String yaml) throws IOException, URISyntaxException {
        Path file = Files.createTempDirectory("github-flows").resolve("flow.yaml");
        Files.writeString(file, yaml);
        file.toFile().deleteOnExit();
        file.getParent().toFile().deleteOnExit();
        TestsUtils.loads(MAIN_TENANT, loader, file.toUri().toURL());
    }

    /** The state of an execution and of each of its tasks, for the message of a failed assertion. */
    static String describe(Execution execution) {
        return execution.getState().getCurrent() + " "
            + execution.getTaskRunList().stream().map(t -> t.getTaskId() + "=" + t.getState().getCurrent()).toList();
    }

    static Map<String, Object> commentEvent(String repository, int issue, long commentId, String login, String body) {
        return Map.of(
            "action", "created",
            "repository", Map.of("full_name", repository),
            "issue", Map.of("number", issue, "assignees", List.of()),
            "comment", Map.of("id", commentId, "body", body, "user", Map.of("login", login, "type", "User"))
        );
    }

    static Map<String, Object> pullRequestEvent(String repository, int number, String login) {
        return Map.of(
            "action", "opened",
            "repository", Map.of("full_name", repository),
            "pull_request", Map.of("number", number, "user", Map.of("login", login, "type", "User"))
        );
    }
}
