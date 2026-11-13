package io.kestra.github;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.serializers.JacksonMapper;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class NotifyUnassignedPullRequestFlowTest {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();
    private static final String REPOSITORY = "kestra-io/kestra";
    private static final String FLOW = "notify-unassigned-pull-request";

    private static FakeGitHub github;
    private static boolean registered;

    @Inject
    private TestRunnerUtils runner;

    @Inject
    private LocalFlowRepositoryLoader loader;

    @BeforeAll
    static void startGitHub() throws IOException {
        github = new FakeGitHub();
    }

    @AfterAll
    static void stopGitHub() {
        github.close();
    }

    @BeforeEach
    void register() throws Exception {
        github.reset();
        if (!registered) {
            GithubFlows.register(loader, GithubFlows.forTest(FLOW, FLOW, github, true, yaml -> yaml));
            registered = true;
        }
    }

    @Test
    void shouldNotifyWhenPullRequestLinksNoIssueAndKeepTheTokenAwayFromSlack() throws Exception {
        github.pullRequests.put(REPOSITORY + "#5", pullRequest("alice", "User", "Fix the thing"));

        Execution execution = run();

        assertThat(execution.getState().getCurrent()).as(GithubFlows.describe(execution)).isEqualTo(State.Type.SUCCESS);
        assertThat(github.slack()).singleElement().satisfies(call ->
        {
            assertThat(call.body().get("text").asText())
                .contains("<https://github.com/kestra-io/kestra/pull/5|kestra-io/kestra#5 Fix the thing>", "`alice`", "It does not link any issue.");
            assertThat(call.authorization()).isNull();
        });
        assertThat(github.calls.stream().filter(c -> !c.path().equals("/slack")))
            .allSatisfy(call -> assertThat(call.authorization()).isEqualTo("Bearer " + FakeGitHub.TOKEN));
    }

    @Test
    void shouldStayQuietWhenTheAuthorIsAssignedToTheLinkedIssue() throws Exception {
        github.pullRequests.put(REPOSITORY + "#5", pullRequest("alice", "User", "Fix", linkedIssue(12, "bob", "alice")));

        run();

        assertThat(github.slack()).isEmpty();
    }

    @Test
    void shouldReportTheLinkedIssuesTheAuthorIsNotAssignedTo() throws Exception {
        github.pullRequests.put(REPOSITORY + "#5", pullRequest("alice", "User", "Fix", linkedIssue(12, "alice"), linkedIssue(13, "bob")));

        run();

        assertThat(github.slack()).singleElement().satisfies(
            call -> assertThat(call.body().get("text").asText())
                .contains("#13> (assigned to bob)").doesNotContain("#12")
        );
    }

    @Test
    void shouldSkipMaintainersByPermissionAndBotsWithoutLookingThemUp() throws Exception {
        github.pullRequests.put(REPOSITORY + "#5", pullRequest("alice", "User", "Fix"));
        github.permissions.put("alice", "write");
        run();
        assertThat(github.slack()).isEmpty();

        github.reset();
        github.pullRequests.put(REPOSITORY + "#5", pullRequest("renovate", "Bot", "Bump"));
        run();
        assertThat(github.slack()).isEmpty();
        assertThat(github.calls).noneMatch(call -> call.path().contains("/permission"));
    }

    @Test
    void shouldStillReportTheVisibleIssuesWhenOneLinkedIssueIsHiddenFromTheToken() throws Exception {
        github.pullRequests.put(REPOSITORY + "#5", pullRequest("alice", "User", "Fix", MAPPER.nullNode(), linkedIssue(12, "bob")));

        Execution execution = run();

        assertThat(execution.getState().getCurrent()).as(GithubFlows.describe(execution)).isEqualTo(State.Type.SUCCESS);
        assertThat(github.slack()).singleElement().satisfies(call -> assertThat(call.body().get("text").asText()).contains("#12> (assigned to bob)"));
    }

    @Test
    void shouldNotReportAPullRequestThatOnlyLinksAnIssueTheTokenCannotSee() throws Exception {
        github.pullRequests.put(REPOSITORY + "#5", pullRequest("alice", "User", "Fix", MAPPER.nullNode()));

        Execution execution = run();

        assertThat(execution.getState().getCurrent()).as(GithubFlows.describe(execution)).isEqualTo(State.Type.SUCCESS);
        assertThat(github.slack()).isEmpty();
    }

    @Test
    void shouldNeutraliseSlackMarkupInTheTitleWithoutCuttingAnEscapeInHalf() throws Exception {
        github.pullRequests.put(REPOSITORY + "#5", pullRequest("alice", "User", "<!channel> Fish & chips " + "<".repeat(200)));

        run();

        assertThat(github.slack()).singleElement().satisfies(call ->
        {
            String text = call.body().get("text").asText();
            assertThat(text).contains("&lt;!channel&gt; Fish &amp; chips &lt;").doesNotContain("<!channel>", "<<");
            assertThat(text).doesNotContainPattern("&(?!amp;|lt;|gt;)");
        });
    }

    private Execution run() throws Exception {
        Map<String, Object> event = GithubFlows.pullRequestEvent(REPOSITORY, 5, "alice");
        return runner.runOne(MAIN_TENANT, GithubFlows.NAMESPACE, FLOW, null, (flow, execution) -> Map.of("event", event), Duration.ofSeconds(60));
    }

    private static ObjectNode pullRequest(String author, String typeName, String title, JsonNode... linkedIssues) {
        ObjectNode pullRequest = MAPPER.createObjectNode()
            .put("url", "https://github.com/kestra-io/kestra/pull/5").put("title", title).put("authorAssociation", "CONTRIBUTOR");
        pullRequest.putObject("author").put("__typename", typeName).put("login", author);
        ArrayNode nodes = pullRequest.putObject("closingIssuesReferences").putArray("nodes");
        for (JsonNode linkedIssue : linkedIssues) {
            nodes.add(linkedIssue);
        }
        return pullRequest;
    }

    private static ObjectNode linkedIssue(int number, String... assignees) {
        ObjectNode issue = MAPPER.createObjectNode().put("number", number).put("url", "https://github.com/kestra-io/kestra/issues/" + number);
        ArrayNode logins = issue.putObject("assignees").putArray("nodes");
        for (String assignee : assignees) {
            logins.add(MAPPER.createObjectNode().put("login", assignee));
        }
        return issue;
    }
}
