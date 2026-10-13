package io.kestra.github;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.TestRunnerUtils;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class AssignIssueFlowTest {
    private static final String REPOSITORY = "kestra-io/kestra";
    private static final String FLOW = "assign-issue";

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
    void shouldAssignAndConfirmWhenContributorHasNoOpenIssue() throws Exception {
        issue(100, new FakeGitHub.Issue());
        comment(1001, 100, "alice", "/assign");

        Execution execution = run("alice", 100, 1001, "/assign");

        assertThat(execution.getState().getCurrent()).as(GithubFlows.describe(execution)).isEqualTo(State.Type.SUCCESS);
        assertThat(github.repository(REPOSITORY).issues.get(100).assignees).containsExactly("alice");
        assertThat(github.commentBodies()).singleElement().satisfies(
            body -> assertThat(body)
                .contains("@alice", "now assigned to you", "1 of 2", "in the kestra-io organization")
                .contains("Closes https://github.com/kestra-io/kestra/issues/100")
        );
        assertThat(github.calls).allSatisfy(call -> assertThat(call.authorization()).isEqualTo("Bearer " + FakeGitHub.TOKEN));
    }

    @Test
    void shouldRefuseAndListOnlyPublicIssuesWhenLimitIsReachedAcrossRepositories() throws Exception {
        issue(100, new FakeGitHub.Issue());
        comment(1001, 100, "alice", "/assign");
        issue(7, new FakeGitHub.Issue().assignedTo("alice"));
        github.repository("kestra-io/plugin-a").issues.put(3, new FakeGitHub.Issue().assignedTo("alice"));
        FakeGitHub.Repository hidden = github.repository("kestra-io/plugin-secret");
        hidden.isPrivate = true;
        hidden.issues.put(1, new FakeGitHub.Issue().assignedTo("alice"));
        FakeGitHub.Repository archived = github.repository("kestra-io/plugin-old");
        archived.archived = true;
        archived.issues.put(2, new FakeGitHub.Issue().assignedTo("alice"));

        Execution execution = run("alice", 100, 1001, "/assign");

        assertThat(execution.getState().getCurrent()).as(GithubFlows.describe(execution)).isEqualTo(State.Type.SUCCESS);
        assertThat(github.repository(REPOSITORY).issues.get(100).assignees).isEmpty();
        assertThat(github.commentBodies()).singleElement().satisfies(
            body -> assertThat(body)
                .contains("you already have 2 open issues", "(#7, kestra-io/plugin-a#3)", "the limit is 2")
                .doesNotContain("plugin-secret", "plugin-old")
        );
    }

    @Test
    void shouldRefuseWhenIssueIsAlreadyAssignedAlthoughThePayloadSaysItIsFree() throws Exception {
        issue(100, new FakeGitHub.Issue().assignedTo("bob"));
        comment(1001, 100, "alice", "/assign");

        run("alice", 100, 1001, "/assign");

        assertThat(github.repository(REPOSITORY).issues.get(100).assignees).containsExactly("bob");
        assertThat(github.commentBodies()).singleElement().satisfies(body -> assertThat(body).contains("already assigned to bob"));
    }

    @Test
    void shouldStaySilentWhenCommenterIsAlreadyAssigned() throws Exception {
        issue(100, new FakeGitHub.Issue().assignedTo("alice"));
        comment(1001, 100, "alice", "/assign");

        Execution execution = run("alice", 100, 1001, "/assign");

        assertThat(execution.getState().getCurrent()).as(GithubFlows.describe(execution)).isEqualTo(State.Type.SUCCESS);
        assertThat(github.writes()).isEmpty();
    }

    @Test
    void shouldConfirmOnceWhenGitHubDeliversTheSameCommentTwice() throws Exception {
        issue(100, new FakeGitHub.Issue());
        comment(1001, 100, "alice", "/assign");

        run("alice", 100, 1001, "/assign");
        Execution second = run("alice", 100, 1001, "/assign");

        assertThat(second.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(github.repository(REPOSITORY).issues.get(100).assignees).containsExactly("alice");
        assertThat(github.commentBodies()).hasSize(1);
        assertThat(github.writes().stream().filter(c -> c.path().endsWith("/assignees"))).hasSize(1);
    }

    @Test
    void shouldNotLimitMaintainersWhoseMembershipIsPrivate() throws Exception {
        issue(100, new FakeGitHub.Issue());
        comment(1001, 100, "mia", "/assign");
        for (int n = 1; n <= 3; n++) {
            issue(n, new FakeGitHub.Issue().assignedTo("mia"));
        }
        github.permissions.put("mia", "write");

        run("mia", 100, 1001, "/assign");

        assertThat(github.repository(REPOSITORY).issues.get(100).assignees).containsExactly("mia");
        assertThat(github.commentBodies()).singleElement().satisfies(body -> assertThat(body).doesNotContain("You have"));
    }

    @Test
    void shouldFailWithoutConfirmingWhenGitHubIgnoresTheAssignee() throws Exception {
        issue(100, new FakeGitHub.Issue());
        comment(1001, 100, "alice", "/assign");
        github.silentlyIgnoredAssignees.add("alice");

        Execution execution = run("alice", 100, 1001, "/assign");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(github.commentBodies()).singleElement().satisfies(
            body -> assertThat(body)
                .contains("did not accept the assignment").doesNotContain("now assigned to you")
        );
    }

    @Test
    void shouldIgnoreCommandThatGitHubNowShowsInsideACodeBlock() throws Exception {
        issue(100, new FakeGitHub.Issue());
        comment(1001, 100, "alice", "Example:\n```\n/assign\n```");

        Execution execution = run("alice", 100, 1001, "/assign");

        assertThat(execution.getState().getCurrent()).as(GithubFlows.describe(execution)).isEqualTo(State.Type.SUCCESS);
        assertThat(github.writes()).isEmpty();
    }

    @Test
    void shouldWriteNothingWhenTheCommentDoesNotExist() throws Exception {
        issue(100, new FakeGitHub.Issue());

        Execution execution = run("mallory", 100, 999, "/assign");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(github.writes()).isEmpty();
    }

    @Test
    void shouldAssignToOnlyOneOfTwoSimultaneousRequestsForTheSameIssue() throws Exception {
        issue(100, new FakeGitHub.Issue());
        comment(1001, 100, "alice", "/assign");
        comment(1002, 100, "bob", "/assign");
        // Both requests would read the issue as free at the same time if nothing serialized them.
        github.issueReadDelayMillis = 300;

        CompletableFuture<Execution> first = CompletableFuture.supplyAsync(() -> unchecked(() -> run("alice", 100, 1001, "/assign")));
        CompletableFuture<Execution> second = CompletableFuture.supplyAsync(() -> unchecked(() -> run("bob", 100, 1002, "/assign")));
        List<Execution> executions = List.of(first.get(), second.get());

        assertThat(executions).extracting(e -> e.getState().getCurrent()).containsOnly(State.Type.SUCCESS);
        assertThat(executions).anySatisfy(e -> assertThat(e.getState().getHistories()).extracting(State.History::getState).contains(State.Type.QUEUED));
        assertThat(github.repository(REPOSITORY).issues.get(100).assignees).hasSize(1);
        assertThat(github.writes().stream().filter(c -> c.path().endsWith("/assignees"))).hasSize(1);
        assertThat(github.commentBodies()).hasSize(2).satisfiesOnlyOnce(body -> assertThat(body).contains("now assigned to you"))
            .satisfiesOnlyOnce(body -> assertThat(body).contains("already assigned to"));
    }

    private void issue(int number, FakeGitHub.Issue issue) {
        github.repository(REPOSITORY).issues.put(number, issue);
    }

    private void comment(long id, int issue, String login, String body) {
        github.repository(REPOSITORY).comments.put(id, new FakeGitHub.Comment(issue, login, "User", body));
    }

    /** Runs the flow with the payload GitHub would send, whatever the comment really says on GitHub. */
    private Execution run(String login, int issue, long commentId, String payloadBody) throws Exception {
        Map<String, Object> event = GithubFlows.commentEvent(REPOSITORY, issue, commentId, login, payloadBody);
        return runner.runOne(MAIN_TENANT, GithubFlows.NAMESPACE, FLOW, null, (flow, execution) -> Map.of("event", event), Duration.ofSeconds(60));
    }

    private interface Task<T> {
        T call() throws Exception;
    }

    private static <T> T unchecked(Task<T> task) {
        try {
            return task.call();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
