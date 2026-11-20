package io.kestra.github;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.services.ConditionService;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.trigger.Webhook;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every GitHub event reaches the flows through the {@code when} of their webhook trigger, so this is where a wrong
 * rule either starts executions for nothing or, worse, starts one for a comment that is not a command.
 */
@KestraTest
class WebhookFiltersTest {
    private static final String KESTRA = "kestra-io/kestra";

    @Inject
    private ConditionService conditionService;

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private ModelValidator modelValidator;

    @Test
    void shouldLoadEveryCommittedFlowWithItsTriggers() throws Exception {
        for (String name : List.of("assign-issue", "notify-unassigned-pull-request", "release-inactive-issues", "remind-inactive-pull-requests")) {
            Flow flow = YamlParser.parse(GithubFlows.source(name), Flow.class);

            assertThat(modelValidator.isValid(flow)).as(name).isEmpty();
            assertThat(flow.getTriggers()).as(name).isNotEmpty();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("comments")
    void shouldStartTheAssignFlowOnlyForARealCommand(String name, Map<String, Object> payload, boolean expected) throws Exception {
        assertThat(triggers("assign-issue", payload)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pullRequests")
    void shouldStartThePullRequestFlowOnlyForANewPullRequestOfAPerson(String name, Map<String, Object> payload, boolean expected) throws Exception {
        assertThat(triggers("notify-unassigned-pull-request", payload)).isEqualTo(expected);
    }

    private boolean triggers(String flowName, Map<String, Object> payload) throws IOException {
        Flow flow = YamlParser.parse(GithubFlows.source(flowName), Flow.class);
        Webhook trigger = (Webhook) flow.getTriggers().getFirst();
        Execution execution = Execution.builder()
            .id(IdUtils.create())
            .tenantId(MAIN_TENANT)
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(1)
            .variables(flow.getVariables())
            .state(new State())
            .trigger(ExecutionTrigger.of(trigger, Webhook.Output.builder().body(payload).build()))
            .build();

        return conditionService.isValid(trigger, flow, runContextFactory.of(flow, execution));
    }

    static Stream<Arguments> comments() {
        String botMessage = "@alice you already have 2 open issues assigned in this repository (#7, #8), and the limit is 2. "
            + "Once one of them is closed or released, comment `/assign` again.";
        return Stream.of(
            Arguments.of("a bare command", comment("/assign"), true),
            Arguments.of("a command in a longer comment", comment("Hi, I would like this one.\n\n/assign\n\nThanks!"), true),
            Arguments.of("a command with CRLF line breaks", comment("Hi\r\n/assign\r\nThanks"), true),
            Arguments.of("upper case with spaces", comment("  /ASSIGN  "), true),
            Arguments.of("three spaces of indentation", comment("   /assign"), true),
            Arguments.of("a command after a code block", comment("trace:\n```\nboom\n```\n/assign"), true),
            Arguments.of("a command between two code blocks", comment("```\na\n```\n/assign\n```\nb\n```"), true),
            Arguments.of("a plugin repository", comment("/assign", "kestra-io/plugin-jdbc"), true),
            Arguments.of("the .github repository", comment("/assign", "kestra-io/.github"), true),
            Arguments.of("a fenced code block", comment("To take an issue:\n```\n/assign\n```"), false),
            Arguments.of("a tilde fence", comment("~~~\n/assign\n~~~"), false),
            Arguments.of("an unterminated fence", comment("```\n/assign\nmore code"), false),
            Arguments.of("a longer fence around a shorter one", comment("````\n```\n/assign\n```\n````"), false),
            Arguments.of("an indented code block", comment("Example:\n\n    /assign\n"), false),
            Arguments.of("a tab-indented command", comment("\t/assign"), false),
            Arguments.of("a command with arguments", comment("/assign @bob"), false),
            Arguments.of("the command inside a sentence", comment("I will /assign myself later"), false),
            Arguments.of("a quoted command", comment("> /assign"), false),
            Arguments.of("a longer word", comment("/assigned"), false),
            Arguments.of("inline code", comment("use `/assign` to take an issue"), false),
            Arguments.of("the bot's own refusal", comment(botMessage), false),
            Arguments.of("an empty comment", comment(""), false),
            Arguments.of("an edited comment", with(comment("/assign"), "action", "edited"), false),
            Arguments.of("a deleted comment", with(comment("/assign"), "action", "deleted"), false),
            Arguments.of("a comment on a pull request conversation", with(comment("/assign"), "issue", Map.of("number", 100, "pull_request", Map.of("url", "x"))), false),
            Arguments.of("a bot", with(comment("/assign"), "comment", Map.of("body", "/assign", "user", Map.of("type", "Bot"))), false),
            Arguments.of("another organization", comment("/assign", "someone/else"), false),
            Arguments.of("a look-alike organization", comment("/assign", "kestra-io-evil/x"), false),
            Arguments.of("an organization embedded in a path", comment("/assign", "evil/kestra-io/x"), false),
            Arguments.of("a repository named ..", comment("/assign", "kestra-io/.."), false),
            Arguments.of("a repository named .", comment("/assign", "kestra-io/."), false),
            Arguments.of("a name with a slash", comment("/assign", "kestra-io/a/b"), false),
            Arguments.of("a name with a query", comment("/assign", "kestra-io/a?b=c"), false),
            Arguments.of("an empty name", comment("/assign", "kestra-io/"), false),
            Arguments.of("GitHub's ping", Map.of("zen", "Keep it logically awesome.", "hook_id", 1), false)
        );
    }

    static Stream<Arguments> pullRequests() {
        return Stream.of(
            Arguments.of("a pull request opened by a person", pullRequest("opened", KESTRA, "User"), true),
            Arguments.of("a plugin repository", pullRequest("opened", "kestra-io/plugin-jdbc", "User"), true),
            Arguments.of("the .github repository", pullRequest("opened", "kestra-io/.github", "User"), true),
            Arguments.of("a push to the pull request", pullRequest("synchronize", KESTRA, "User"), false),
            Arguments.of("a closed pull request", pullRequest("closed", KESTRA, "User"), false),
            Arguments.of("a bot", pullRequest("opened", KESTRA, "Bot"), false),
            Arguments.of("another organization", pullRequest("opened", "x/y", "User"), false),
            Arguments.of("a look-alike organization", pullRequest("opened", "kestra-io-evil/y", "User"), false),
            Arguments.of("a repository named ..", pullRequest("opened", "kestra-io/..", "User"), false),
            Arguments.of("an issue comment", comment("/assign"), false),
            Arguments.of("GitHub's ping", Map.of("zen", "Keep it logically awesome.", "hook_id", 1), false)
        );
    }

    private static Map<String, Object> comment(String body) {
        return comment(body, KESTRA);
    }

    private static Map<String, Object> comment(String body, String repository) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "created");
        payload.put("repository", Map.of("full_name", repository));
        payload.put("issue", Map.of("number", 100));
        payload.put("comment", Map.of("id", 1001, "body", body, "user", Map.of("login", "alice", "type", "User")));
        return payload;
    }

    private static Map<String, Object> with(Map<String, Object> payload, String key, Object value) {
        Map<String, Object> copy = new HashMap<>(payload);
        copy.put(key, value);
        return copy;
    }

    private static Map<String, Object> pullRequest(String action, String repository, String type) {
        return Map.of(
            "action", action,
            "repository", Map.of("full_name", repository),
            "pull_request", Map.of("number", 5, "user", Map.of("login", "alice", "type", type))
        );
    }
}
