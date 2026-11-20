package io.kestra.github;

import java.io.IOException;
import java.time.Duration;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.serializers.YamlParser;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class RemindInactivePullRequestsFlowTest {
    private static final String REPOSITORY = "kestra-io/kestra";
    private static final String FLOW = "remind-inactive-pull-requests";
    private static final String PAGED = FLOW + "-paged";
    private static final String MARKER = "<!-- remind-inactive-pull-requests:";

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
            GithubFlows.register(loader, GithubFlows.forTest(FLOW, FLOW, github, false, yaml -> yaml));
            // Two results per page: enough to see how reading the pages interacts with acting on the pull requests.
            GithubFlows.register(
                loader, GithubFlows.forTest(
                    FLOW, PAGED, github, false,
                    yaml -> yaml.replace("per_page=100&page", "per_page=2&page").replace("pages: [10, 9, 8, 7, 6, 5, 4, 3, 2, 1]", "pages: [3, 2, 1]")
                )
            );
            registered = true;
        }
    }

    @Test
    void shouldShipDisabled() throws Exception {
        Flow flow = YamlParser.parse(GithubFlows.source(FLOW), Flow.class);

        assertThat(flow.getTriggers()).singleElement().satisfies(trigger -> assertThat(trigger.isDisabled()).isTrue());
    }

    @Test
    void shouldAskTheAuthorToAnswerWhenSomeoneElseWroteLast() throws Exception {
        pullRequest(5, new FakeGitHub.PullRequest().quietFor(10).reviewedBy("bob", "User", 9).updatedDaysAgo(9));

        Execution execution = run(FLOW);

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(github.commentBodies()).singleElement().satisfies(
            body -> assertThat(body)
                .startsWith(MARKER + "1 -->\n@alice this pull request has had no activity for 7 days since @bob last wrote on it")
                .contains("push an update or reply here")
        );
    }

    @Test
    void shouldAskTheReviewersAndAssigneesWhenTheAuthorWroteLast() throws Exception {
        pullRequest(5, new FakeGitHub.PullRequest().quietFor(10).reviewRequestedFrom("bob").assignedTo("carol", "alice"));

        run(FLOW);

        assertThat(github.commentBodies()).singleElement().satisfies(
            body -> assertThat(body)
                .startsWith(MARKER + "1 -->\n@bob, @carol this pull request by @alice has been waiting for 7 days without activity")
        );
    }

    @Test
    void shouldSayThatAMaintainerIsNeededWhenNobodyIsAssignedOrAskedToReview() throws Exception {
        pullRequest(5, new FakeGitHub.PullRequest().quietFor(10));

        run(FLOW);

        assertThat(github.commentBodies()).singleElement().satisfies(
            body -> assertThat(body)
                .contains("A maintainer is needed on this pull request by @alice", "nobody is assigned or asked to review it")
        );
    }

    @Test
    void shouldRemindOnceThenEscalateAfterAnotherWeekThenStaySilent() throws Exception {
        FakeGitHub.PullRequest pullRequest = new FakeGitHub.PullRequest().quietFor(10).reviewRequestedFrom("bob");
        pullRequest(5, pullRequest);

        run(FLOW);
        run(FLOW);
        assertThat(github.commentBodies()).as("one reminder, however often the flow runs").hasSize(1);

        // A week passes without a word: the reminder is now a week old and the pull request has been quiet for 17 days.
        pullRequest.createdAt = FakeGitHub.ago(17);
        pullRequest.commitAt = FakeGitHub.ago(17);
        pullRequest.comments.set(0, new FakeGitHub.PullRequest.Note("maintainer-token-owner", "User", FakeGitHub.ago(8), pullRequest.comments.getFirst().body()));
        pullRequest.updatedAt = FakeGitHub.ago(8);

        run(FLOW);
        assertThat(github.commentBodies()).hasSize(2).last().satisfies(
            body -> assertThat(body)
                .startsWith(MARKER + "2 -->\n@bob this pull request by @alice is still waiting after 14 days")
        );

        pullRequest.comments.set(1, new FakeGitHub.PullRequest.Note("maintainer-token-owner", "User", FakeGitHub.ago(8), pullRequest.comments.get(1).body()));
        pullRequest.updatedAt = FakeGitHub.ago(8);
        run(FLOW);
        assertThat(github.commentBodies()).as("nothing after the second reminder until something happens").hasSize(2);
    }

    @Test
    void shouldStartAgainWhenSomeoneWritesAfterTheReminders() throws Exception {
        FakeGitHub.PullRequest pullRequest = new FakeGitHub.PullRequest().quietFor(40).reviewRequestedFrom("bob");
        pullRequest.commentedBy("maintainer-token-owner", "User", 30, MARKER + "1 -->\n@bob old reminder");
        pullRequest.commentedBy("maintainer-token-owner", "User", 23, MARKER + "2 -->\n@bob old escalation");
        pullRequest.commentedBy("alice", "User", 10, "Sorry, I was away. Pushing a fix soon.");
        pullRequest.updatedDaysAgo(10);
        pullRequest(5, pullRequest);

        run(FLOW);

        assertThat(github.commentBodies()).singleElement().satisfies(body -> assertThat(body).startsWith(MARKER + "1 -->"));
    }

    @Test
    void shouldNotLetBotsRestartTheClock() throws Exception {
        FakeGitHub.PullRequest pullRequest = new FakeGitHub.PullRequest().quietFor(10).reviewRequestedFrom("bob");
        pullRequest.commentedBy("kestrabot", "Bot", 2, "Welcome! Your pull request was labelled.");
        pullRequest.reviewedBy("copilot-pull-request-reviewer", "Bot", 1);
        pullRequest.updatedDaysAgo(8);
        pullRequest(5, pullRequest);

        run(FLOW);

        assertThat(github.commentBodies()).singleElement().satisfies(body -> assertThat(body).startsWith(MARKER + "1 -->"));
    }

    @Test
    void shouldCountCommitsReviewsAndCommentsOfPeopleAsActivity() throws Exception {
        // Search still shows them as old, but each had something real happen in the last 3 days.
        pullRequest(1, new FakeGitHub.PullRequest().quietFor(20).pushedDaysAgo(3).updatedDaysAgo(20));
        pullRequest(2, new FakeGitHub.PullRequest().quietFor(20).reviewedBy("bob", "User", 3).updatedDaysAgo(20));
        pullRequest(3, new FakeGitHub.PullRequest().quietFor(20).commentedBy("bob", "User", 3, "Looks good, one question").updatedDaysAgo(20));
        pullRequest(4, new FakeGitHub.PullRequest().quietFor(20).eventBy("ReadyForReviewEvent", "alice", 3).updatedDaysAgo(20));

        run(FLOW);

        assertThat(github.writes()).isEmpty();
    }

    @Test
    void shouldLeaveDraftsBotsMaintainersAndExemptLabelsAlone() throws Exception {
        pullRequest(1, new FakeGitHub.PullRequest().quietFor(10).asDraft());
        pullRequest(2, new FakeGitHub.PullRequest().quietFor(10).byBot("renovate"));
        pullRequest(3, new FakeGitHub.PullRequest().quietFor(10).by("mia"));
        pullRequest(4, new FakeGitHub.PullRequest().quietFor(10).labelled("kind/do-not-merge"));
        pullRequest(5, new FakeGitHub.PullRequest().quietFor(10).labelled("Blocked"));
        github.permissions.put("mia", "write");

        run(FLOW);

        assertThat(github.writes()).isEmpty();
    }

    @Test
    void shouldNotEvenLookAtDependabotPullRequests() throws Exception {
        pullRequest(1, new FakeGitHub.PullRequest().quietFor(10).byBot("dependabot[bot]"));

        run(FLOW);

        assertThat(github.calls).noneMatch(call -> call.path().equals("/graphql"));
    }

    @Test
    void shouldLeaveAbandonedPullRequestsToTheMaintainers() throws Exception {
        pullRequest(1, new FakeGitHub.PullRequest().quietFor(100));
        // Updated recently by a bot, but nothing real has happened for 100 days.
        pullRequest(2, new FakeGitHub.PullRequest().quietFor(100).updatedDaysAgo(20));

        run(FLOW);

        assertThat(github.writes()).isEmpty();
    }

    @Test
    void shouldOnlyEverComment() throws Exception {
        pullRequest(1, new FakeGitHub.PullRequest().quietFor(10).assignedTo("bob").reviewRequestedFrom("bob"));
        pullRequest(2, new FakeGitHub.PullRequest().quietFor(30).reviewedBy("bob", "User", 25).updatedDaysAgo(25).assignedTo("bob"));

        run(FLOW);
        run(FLOW);

        assertThat(github.writes()).isNotEmpty().allSatisfy(call ->
        {
            assertThat(call.method()).isEqualTo("POST");
            assertThat(call.path()).endsWith("/comments");
        });
        assertThat(github.repository(REPOSITORY).pullRequests.values()).allSatisfy(pr ->
        {
            assertThat(pr.state).isEqualTo("OPEN");
            assertThat(pr.assignees).containsExactly("bob");
        });
    }

    @Test
    void shouldReadEveryPageAndReachAContributorBehindMaintainersPullRequests() throws Exception {
        pullRequest(1, new FakeGitHub.PullRequest().quietFor(40).by("mia"));
        pullRequest(2, new FakeGitHub.PullRequest().quietFor(39).by("mia"));
        pullRequest(3, new FakeGitHub.PullRequest().quietFor(38).by("mia"));
        pullRequest(4, new FakeGitHub.PullRequest().quietFor(37).by("alice"));
        pullRequest(5, new FakeGitHub.PullRequest().quietFor(36).by("bob"));
        github.permissions.put("mia", "write");

        run(PAGED);

        assertThat(github.commentBodies()).hasSize(2);
        assertThat(github.commentBodies().stream().map(body -> body.contains("by @alice") || body.contains("by @bob")))
            .containsOnly(true);
    }

    @Test
    void shouldRemindEveryPullRequestOnceAlthoughRemindersRemoveThemFromTheResults() throws Exception {
        IntStream.rangeClosed(1, 5).forEach(n -> pullRequest(n, new FakeGitHub.PullRequest().quietFor(40 - n).by("user" + n)));

        run(PAGED);

        assertThat(github.commentBodies()).hasSize(5);
    }

    @Test
    void shouldKeepGoingAfterOneLookupFails() throws Exception {
        pullRequest(1, new FakeGitHub.PullRequest().quietFor(20));
        pullRequest(2, new FakeGitHub.PullRequest().quietFor(10).by("bob"));
        github.failingIssueLookups.add(1);

        Execution execution = run(FLOW);

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(github.commentBodies()).singleElement().satisfies(body -> assertThat(body).contains("@bob"));
    }

    @Test
    void shouldOnlyLookAtPublicRepositoriesThatAreNotArchived() throws Exception {
        FakeGitHub.Repository hidden = github.repository("kestra-io/plugin-secret");
        hidden.isPrivate = true;
        hidden.pullRequests.put(1, new FakeGitHub.PullRequest().quietFor(10));
        FakeGitHub.Repository archived = github.repository("kestra-io/plugin-old");
        archived.archived = true;
        archived.pullRequests.put(1, new FakeGitHub.PullRequest().quietFor(10));
        github.repository("kestra-io/plugin-a").pullRequests.put(1, new FakeGitHub.PullRequest().quietFor(10));

        run(FLOW);

        assertThat(github.writes()).singleElement().satisfies(call -> assertThat(call.path()).contains("/kestra-io/plugin-a/"));
    }

    private void pullRequest(int number, FakeGitHub.PullRequest pullRequest) {
        github.repository(REPOSITORY).pullRequests.put(number, pullRequest);
    }

    private Execution run(String flow) throws Exception {
        return runner.runOne(MAIN_TENANT, GithubFlows.NAMESPACE, flow, null, null, Duration.ofSeconds(120));
    }
}
