package io.kestra.github;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
class ReleaseInactiveIssuesFlowTest {
    private static final String REPOSITORY = "kestra-io/kestra";
    private static final String FLOW = "release-inactive-issues";
    private static final String LABELED = FLOW + "-labeled";
    private static final String PAGED = FLOW + "-paged";
    private static final String MARKER = "<!-- release-inactive-issues:reminder:";

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
            GithubFlows.register(
                loader, GithubFlows.forTest(
                    FLOW, LABELED, github, false,
                    yaml -> yaml.replace("releasedLabel: ''", "releasedLabel: 'help wanted'")
                )
            );
            // Two results per page, three pages: enough to see how reading the pages interacts with acting on the issues.
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
    void shouldRemindTheAssigneeOfAnIdleIssueWithAMarkerNamingThem() throws Exception {
        issue(7, new FakeGitHub.Issue().assignedTo("alice").idleDays(8));

        Execution execution = run(FLOW);

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(github.commentBodies()).singleElement().satisfies(
            body -> assertThat(body)
                .startsWith(MARKER + "alice -->\n@alice").contains("no activity for 7 days", "unassigned so that someone else")
        );
        assertThat(github.repository(REPOSITORY).issues.get(7).assignees).containsExactly("alice");
    }

    @Test
    void shouldLeaveAnIssueActiveInTheLastWeekAlone() throws Exception {
        issue(7, new FakeGitHub.Issue().assignedTo("alice").idleDays(3));

        run(FLOW);

        assertThat(github.writes()).isEmpty();
    }

    @Test
    void shouldReleaseAfterAnUnansweredReminderAndAnnounceItBeforeAddingTheLabel() throws Exception {
        issue(7, new FakeGitHub.Issue().assignedTo("alice").idleDays(8).lastComment(reminderFor("alice")));

        run(LABELED);

        assertThat(github.repository(REPOSITORY).issues.get(7).assignees).isEmpty();
        assertThat(github.writes()).extracting(call -> call.method() + " " + call.path().substring(call.path().lastIndexOf('/') + 1))
            .containsExactly("DELETE assignees", "POST comments", "POST labels");
        assertThat(github.commentBodies()).singleElement().satisfies(
            body -> assertThat(body)
                .contains("@alice", "no activity on this issue for 14 days", "now unassigned")
        );
    }

    @Test
    void shouldAnnounceTheReleaseEvenWhenLabellingFails() throws Exception {
        issue(7, new FakeGitHub.Issue().assignedTo("alice").idleDays(8).lastComment(reminderFor("alice")));
        github.failingLabels = true;

        run(LABELED);

        assertThat(github.repository(REPOSITORY).issues.get(7).assignees).isEmpty();
        assertThat(github.commentBodies()).singleElement().satisfies(body -> assertThat(body).contains("now unassigned"));
    }

    @Test
    void shouldNotAnnounceAnUnassignmentThatGitHubIgnored() throws Exception {
        issue(7, new FakeGitHub.Issue().assignedTo("alice").idleDays(8).lastComment(reminderFor("alice")));
        github.ignoringUnassignments = true;

        run(LABELED);

        assertThat(github.repository(REPOSITORY).issues.get(7).assignees).containsExactly("alice");
        assertThat(github.commentBodies()).isEmpty();
        assertThat(github.writes()).extracting(call -> call.method()).containsOnly("DELETE");
    }

    @Test
    void shouldRemindANewAssigneeInsteadOfReleasingThem() throws Exception {
        issue(7, new FakeGitHub.Issue().assignedTo("bob").idleDays(8).lastComment(reminderFor("alice")));

        run(FLOW);

        assertThat(github.repository(REPOSITORY).issues.get(7).assignees).containsExactly("bob");
        assertThat(github.commentBodies()).singleElement().satisfies(body -> assertThat(body).startsWith(MARKER + "bob -->\n@bob"));
    }

    @Test
    void shouldRecogniseAReminderWhateverTheOrderOfTheAssignees() throws Exception {
        issue(7, new FakeGitHub.Issue().assignedTo("bob", "alice").idleDays(8).lastComment(reminderFor("alice", "bob")));

        run(FLOW);

        assertThat(github.repository(REPOSITORY).issues.get(7).assignees).isEmpty();
    }

    @Test
    void shouldLeaveMaintainersOpenPullRequestsAndExemptLabelsAlone() throws Exception {
        issue(1, new FakeGitHub.Issue().assignedTo("mia").idleDays(30));
        issue(2, new FakeGitHub.Issue().assignedTo("alice").idleDays(30).withOpenPullRequest());
        issue(3, new FakeGitHub.Issue().assignedTo("alice").idleDays(30).labelled("Blocked"));
        github.permissions.put("mia", "write");

        run(FLOW);

        assertThat(github.writes()).isEmpty();
    }

    @Test
    void shouldNotTreatAFullPageOfCollaboratorsAsTheWholeList() throws Exception {
        IntStream.range(0, 100).forEach(n -> github.permissions.put("a%03d".formatted(n), "write"));
        github.permissions.put("zed", "write");
        issue(7, new FakeGitHub.Issue().assignedTo("zed").idleDays(30));

        run(FLOW);

        assertThat(github.writes()).isEmpty();
    }

    @Test
    void shouldActOnTheIssueAsItIsNowNotAsTheSearchShowedIt() throws Exception {
        issue(1, new FakeGitHub.Issue().assignedTo("alice").idleDays(8).closed());
        issue(2, new FakeGitHub.Issue().assignedTo("alice").idleDays(0.01));
        issue(3, new FakeGitHub.Issue().assignedTo("alice").idleDays(8).labelled("blocked"));
        issue(4, new FakeGitHub.Issue().idleDays(8));
        String idle = new FakeGitHub.Issue().idleDays(8).updatedAt;
        github.searchSnapshots.put(1, Map.of("state", "open"));
        github.searchSnapshots.put(2, Map.of("updatedAt", idle));
        github.searchSnapshots.put(3, Map.of("labels", List.of()));
        github.searchSnapshots.put(4, Map.of("assignees", List.of("alice")));

        run(FLOW);

        assertThat(github.writes()).isEmpty();
    }

    @Test
    void shouldRemindWhoIsAssignedNowNotWhoTheSearchShowed() throws Exception {
        issue(7, new FakeGitHub.Issue().assignedTo("bob").idleDays(8));
        github.searchSnapshots.put(7, Map.of("assignees", List.of("alice")));

        run(FLOW);

        assertThat(github.commentBodies()).singleElement().satisfies(body -> assertThat(body).contains("@bob").doesNotContain("@alice"));
    }

    @Test
    void shouldReadEveryPageAndRemindEachIssueOnceAlthoughRemindersRemoveIssuesFromTheResults() throws Exception {
        for (int n = 1; n <= 5; n++) {
            issue(n, new FakeGitHub.Issue().assignedTo("user" + n).idleDays(40 - n));
        }

        run(PAGED);

        assertThat(github.commentBodies()).hasSize(5).allSatisfy(body -> assertThat(body).startsWith(MARKER));
        assertThat(github.commentBodies().stream().map(body -> body.split("\n")[1].split(" ")[0])).containsExactlyInAnyOrder("@user1", "@user2", "@user3", "@user4", "@user5");
    }

    @Test
    void shouldNotLetMaintainersIssuesAtTheFrontStarveAContributor() throws Exception {
        issue(1, new FakeGitHub.Issue().assignedTo("mia").idleDays(30));
        issue(2, new FakeGitHub.Issue().assignedTo("mia").idleDays(29));
        issue(3, new FakeGitHub.Issue().assignedTo("mia").idleDays(28));
        issue(4, new FakeGitHub.Issue().assignedTo("alice").idleDays(10));
        github.permissions.put("mia", "write");

        run(PAGED);

        assertThat(github.commentBodies()).singleElement().satisfies(body -> assertThat(body).contains("@alice"));
    }

    @Test
    void shouldKeepGoingAfterOneIssueFails() throws Exception {
        issue(7, new FakeGitHub.Issue().assignedTo("alice").idleDays(20));
        issue(8, new FakeGitHub.Issue().assignedTo("bob").idleDays(10));
        github.failingIssueLookups.add(7);

        Execution execution = run(FLOW);

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(github.commentBodies()).singleElement().satisfies(body -> assertThat(body).contains("@bob"));
    }

    @Test
    void shouldOnlyLookAtPublicRepositoriesThatAreNotArchived() throws Exception {
        FakeGitHub.Repository hidden = github.repository("kestra-io/plugin-secret");
        hidden.isPrivate = true;
        hidden.issues.put(1, new FakeGitHub.Issue().assignedTo("alice").idleDays(30));
        FakeGitHub.Repository archived = github.repository("kestra-io/plugin-old");
        archived.archived = true;
        archived.issues.put(1, new FakeGitHub.Issue().assignedTo("alice").idleDays(30));
        github.repository("kestra-io/plugin-a").issues.put(1, new FakeGitHub.Issue().assignedTo("alice").idleDays(30));

        run(FLOW);

        assertThat(github.writes()).singleElement().satisfies(call -> assertThat(call.path()).contains("/kestra-io/plugin-a/"));
    }

    private void issue(int number, FakeGitHub.Issue issue) {
        github.repository(REPOSITORY).issues.put(number, issue);
    }

    private static String reminderFor(String... logins) {
        return MARKER + String.join(",", logins.length > 1 ? List.of(logins).stream().sorted().toList() : List.of(logins)) + " -->\n"
            + "@" + String.join(", @", logins) + " this issue has seen no activity for 7 days.";
    }

    private Execution run(String flow) throws Exception {
        return runner.runOne(MAIN_TENANT, GithubFlows.NAMESPACE, flow, null, null, Duration.ofSeconds(120));
    }
}
