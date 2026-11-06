package io.kestra.github;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.kestra.core.serializers.JacksonMapper;

/**
 * The slice of the GitHub REST and GraphQL APIs, and of a Slack incoming webhook, that the flows in
 * {@code .github/kestra} talk to, kept in memory. It records every call, so a test can assert on what GitHub
 * received as well as on what the execution reports.
 */
final class FakeGitHub implements AutoCloseable {
    static final String TOKEN = "test-token";
    static final String EPOCH = "2020-01-01T00:00:00Z";

    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();
    private static final Pattern REPO = Pattern.compile("/repos/([^/]+/[^/]+)");

    record Call(String method, String path, String query, String authorization, JsonNode body) {
    }

    static final class Issue {
        String state = "open";
        List<String> assignees = new ArrayList<>();
        boolean pullRequest;
        String updatedAt = EPOCH;
        List<String> labels = new ArrayList<>();
        String lastComment;
        int openPullRequests;

        Issue assignedTo(String... logins) {
            assignees.addAll(List.of(logins));
            return this;
        }

        Issue closed() {
            state = "closed";
            return this;
        }

        Issue pullRequest() {
            pullRequest = true;
            return this;
        }

        Issue idleDays(double days) {
            updatedAt = Instant.now().minusSeconds((long) (days * 86_400)).toString().replaceAll("\\.\\d+Z$", "Z");
            return this;
        }

        Issue labelled(String... names) {
            labels.addAll(List.of(names));
            return this;
        }

        Issue lastComment(String body) {
            lastComment = body;
            return this;
        }

        Issue withOpenPullRequest() {
            openPullRequests = 1;
            return this;
        }
    }

    record Comment(int issue, String login, String type, String body) {
    }

    /** A pull request as the reminder flow sees it: its activity, who it waits for and what search shows of it. */
    static final class PullRequest {
        record Review(String login, String type, String submittedAt) {
        }

        record Note(String login, String type, String createdAt, String body) {
        }

        record Event(String kind, String actor, String createdAt) {
        }

        String state = "OPEN";
        boolean draft;
        String author = "alice";
        String authorType = "User";
        String createdAt = EPOCH;
        String updatedAt = EPOCH;
        String commitAt = EPOCH;
        String committer;
        final List<String> labels = new ArrayList<>();
        final List<String> assignees = new ArrayList<>();
        final List<String> requestedReviewers = new ArrayList<>();
        final List<Review> reviews = new ArrayList<>();
        final List<Note> comments = new ArrayList<>();
        final List<Event> events = new ArrayList<>();

        PullRequest by(String login) {
            author = login;
            return this;
        }

        PullRequest byBot(String login) {
            author = login;
            authorType = "Bot";
            return this;
        }

        PullRequest asDraft() {
            draft = true;
            return this;
        }

        PullRequest labelled(String... names) {
            labels.addAll(List.of(names));
            return this;
        }

        PullRequest assignedTo(String... logins) {
            assignees.addAll(List.of(logins));
            return this;
        }

        PullRequest reviewRequestedFrom(String... logins) {
            requestedReviewers.addAll(List.of(logins));
            return this;
        }

        /** Nothing has happened for this long: the pull request, its last commit and its update date all date back. */
        PullRequest quietFor(double days) {
            createdAt = ago(days);
            commitAt = ago(days);
            updatedAt = ago(days);
            return this;
        }

        /** Only the date that search shows moves, as when a bot touches the pull request. */
        PullRequest updatedDaysAgo(double days) {
            updatedAt = ago(days);
            return this;
        }

        PullRequest pushedDaysAgo(double days) {
            commitAt = ago(days);
            return this;
        }

        PullRequest pushedBy(String login) {
            committer = login;
            return this;
        }

        PullRequest reviewedBy(String login, String type, double daysAgo) {
            reviews.add(new Review(login, type, ago(daysAgo)));
            return this;
        }

        PullRequest commentedBy(String login, String type, double daysAgo, String body) {
            comments.add(new Note(login, type, ago(daysAgo), body));
            return this;
        }

        PullRequest eventBy(String kind, String actor, double daysAgo) {
            events.add(new Event(kind, actor, ago(daysAgo)));
            return this;
        }
    }

    static final class Repository {
        final Map<Integer, Issue> issues = new LinkedHashMap<>();
        final Map<Long, Comment> comments = new HashMap<>();
        final Map<Integer, PullRequest> pullRequests = new LinkedHashMap<>();
        boolean archived;
        boolean isPrivate;
    }

    final List<Call> calls = new CopyOnWriteArrayList<>();
    final Map<String, Repository> repositories = new HashMap<>();
    final Map<String, String> permissions = new HashMap<>();
    final Map<String, JsonNode> pullRequests = new HashMap<>();
    final Set<String> silentlyIgnoredAssignees = new HashSet<>();
    final Set<Integer> failingIssueLookups = new HashSet<>();
    final Map<Integer, Map<String, Object>> searchSnapshots = new HashMap<>();
    volatile boolean failingLabels;
    volatile boolean ignoringUnassignments;
    volatile long issueReadDelayMillis;
    volatile boolean incompleteSearch;

    private final HttpServer server;

    private static String now() {
        return ago(0);
    }

    static String ago(double days) {
        return Instant.now().minusSeconds((long) (days * 86_400)).toString().replaceAll("\\.\\d+Z$", "Z");
    }

    FakeGitHub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    synchronized void reset() {
        calls.clear();
        repositories.clear();
        permissions.clear();
        pullRequests.clear();
        silentlyIgnoredAssignees.clear();
        failingIssueLookups.clear();
        searchSnapshots.clear();
        failingLabels = false;
        ignoringUnassignments = false;
        issueReadDelayMillis = 0;
        incompleteSearch = false;
    }

    synchronized Repository repository(String fullName) {
        return repositories.computeIfAbsent(fullName, k -> new Repository());
    }

    List<Call> writes() {
        return calls.stream()
            .filter(c -> !c.method().equals("GET") && !c.path().equals("/graphql") && !c.path().equals("/slack"))
            .toList();
    }

    List<String> commentBodies() {
        return writes().stream().filter(c -> c.path().endsWith("/comments")).map(c -> c.body().get("body").asText()).toList();
    }

    List<Call> slack() {
        return calls.stream().filter(c -> c.path().equals("/slack")).toList();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            JsonNode body = exchange.getRequestMethod().equals("GET") ? null : readBody(exchange);
            URI uri = exchange.getRequestURI();
            Call call = new Call(
                exchange.getRequestMethod(), uri.getPath(), uri.getRawQuery(),
                exchange.getRequestHeaders().getFirst("Authorization"), body
            );
            JsonNode response;
            int status = 200;
            synchronized (this) {
                calls.add(call);
                response = route(call);
                if (response == null) {
                    status = 404;
                    response = MAPPER.createObjectNode().put("message", "Not Found");
                } else if (response.has("__status")) {
                    status = response.get("__status").asInt();
                    response = MAPPER.createObjectNode().put("message", "error");
                }
            }
            if (issueReadDelayMillis > 0 && call.path().matches("/repos/[^/]+/[^/]+/issues/\\d+") && call.method().equals("GET")) {
                Thread.sleep(issueReadDelayMillis);
            }
            byte[] bytes = MAPPER.writeValueAsBytes(response);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (Exception e) {
            exchange.sendResponseHeaders(500, -1);
        } finally {
            exchange.close();
        }
    }

    private static JsonNode readBody(HttpExchange exchange) throws IOException {
        byte[] raw = exchange.getRequestBody().readAllBytes();
        return raw.length == 0 ? null : MAPPER.readTree(raw);
    }

    private JsonNode route(Call call) {
        String path = call.path();
        String method = call.method();
        if (path.equals("/graphql")) {
            return graphql(call.body());
        }
        if (path.equals("/slack")) {
            return MAPPER.createObjectNode();
        }
        if (path.equals("/search/issues")) {
            return search(call.query());
        }
        Matcher repo = REPO.matcher(path);
        if (!repo.lookingAt()) {
            return null;
        }
        String name = repo.group(1);
        String rest = path.substring(repo.end());
        Repository repository = repository(name);
        Matcher m;
        if (method.equals("GET") && (m = Pattern.compile("/issues/comments/(\\d+)").matcher(rest)).matches()) {
            Comment c = repository.comments.get(Long.parseLong(m.group(1)));
            return c == null ? null
                : MAPPER.createObjectNode()
                    .put("id", Long.parseLong(m.group(1))).put("body", c.body())
                    .put("issue_url", "https://api.github.com/repos/" + name + "/issues/" + c.issue())
                    .<ObjectNode> set("user", MAPPER.createObjectNode().put("login", c.login()).put("type", c.type()));
        }
        if (method.equals("GET") && (m = Pattern.compile("/issues/(\\d+)").matcher(rest)).matches()) {
            Issue issue = repository.issues.get(Integer.parseInt(m.group(1)));
            return issue == null ? null : issueJson(issue, Integer.parseInt(m.group(1)));
        }
        if (method.equals("GET") && rest.equals("/issues")) {
            Map<String, String> q = query(call.query());
            ArrayNode out = MAPPER.createArrayNode();
            repository.issues.forEach((n, i) ->
            {
                if (i.assignees.contains(q.get("assignee")) && i.state.equals(q.getOrDefault("state", "open"))) {
                    out.add(issueJson(i, n));
                }
            });
            return out;
        }
        if (method.equals("GET") && (m = Pattern.compile("/collaborators/([^/]+)/permission").matcher(rest)).matches()) {
            String permission = permission(name, m.group(1));
            return MAPPER.createObjectNode().put("permission", permission).put("role_name", permission);
        }
        if (method.equals("GET") && rest.equals("/collaborators")) {
            ArrayNode out = MAPPER.createArrayNode();
            permissions.keySet().stream()
                .map(k -> k.substring(k.indexOf(':') + 1))
                .distinct().sorted()
                .filter(login -> List.of("write", "admin").contains(permission(name, login)))
                .limit(100)
                .forEach(login -> out.add(MAPPER.createObjectNode().put("login", login)));
            return out;
        }
        if (method.equals("POST") && (m = Pattern.compile("/issues/(\\d+)/assignees").matcher(rest)).matches()) {
            Issue issue = repository.issues.get(Integer.parseInt(m.group(1)));
            if (issue == null) {
                return null;
            }
            for (JsonNode login : call.body().get("assignees")) {
                if (!silentlyIgnoredAssignees.contains(login.asText()) && !issue.assignees.contains(login.asText())) {
                    issue.assignees.add(login.asText());
                }
            }
            issue.updatedAt = now();
            return issueJson(issue, Integer.parseInt(m.group(1)));
        }
        if (method.equals("DELETE") && (m = Pattern.compile("/issues/(\\d+)/assignees").matcher(rest)).matches()) {
            Issue issue = repository.issues.get(Integer.parseInt(m.group(1)));
            for (JsonNode login : call.body().get("assignees")) {
                if (!ignoringUnassignments) {
                    issue.assignees.remove(login.asText());
                }
            }
            issue.updatedAt = now();
            return issueJson(issue, Integer.parseInt(m.group(1)));
        }
        if (
            method.equals("POST") && (m = Pattern.compile("/issues/(\\d+)/comments").matcher(rest)).matches()
                && repository.pullRequests.containsKey(Integer.parseInt(m.group(1)))
        ) {
            PullRequest pullRequest = repository.pullRequests.get(Integer.parseInt(m.group(1)));
            pullRequest.comments.add(new PullRequest.Note("maintainer-token-owner", "User", now(), call.body().get("body").asText()));
            pullRequest.updatedAt = now();
            return MAPPER.createObjectNode().put("id", 1);
        }
        if (method.equals("POST") && (m = Pattern.compile("/issues/(\\d+)/comments").matcher(rest)).matches()) {
            Issue issue = repository.issues.get(Integer.parseInt(m.group(1)));
            issue.lastComment = call.body().get("body").asText();
            issue.updatedAt = now();
            return MAPPER.createObjectNode().put("id", 1);
        }
        if (method.equals("POST") && (m = Pattern.compile("/issues/(\\d+)/labels").matcher(rest)).matches()) {
            if (failingLabels) {
                return MAPPER.createObjectNode().put("__status", 500);
            }
            Issue issue = repository.issues.get(Integer.parseInt(m.group(1)));
            call.body().get("labels").forEach(l -> issue.labels.add(l.asText()));
            issue.updatedAt = now();
            return MAPPER.createArrayNode();
        }
        return null;
    }

    private String permission(String repository, String login) {
        return permissions.getOrDefault(repository + ":" + login, permissions.getOrDefault(login, "read"));
    }

    private static ObjectNode issueJson(Issue issue, int number) {
        ObjectNode node = MAPPER.createObjectNode().put("number", number).put("state", issue.state).put("updated_at", issue.updatedAt);
        ArrayNode assignees = node.putArray("assignees");
        issue.assignees.forEach(a -> assignees.add(MAPPER.createObjectNode().put("login", a)));
        ArrayNode labels = node.putArray("labels");
        issue.labels.forEach(l -> labels.add(MAPPER.createObjectNode().put("name", l)));
        if (issue.pullRequest) {
            node.putObject("pull_request").put("url", "https://api.github.com/pulls/" + number);
        }
        return node;
    }

    private JsonNode search(String rawQuery) {
        String q = query(rawQuery).get("q");
        if (q.contains("is:pr")) {
            return searchPullRequests(q, query(rawQuery));
        }
        Map<String, String> params = query(rawQuery);
        String organization = find(q, "org:(\\S+)");
        String assignee = find(q, "assignee:(\\S+)");
        Set<String> excludedRepositories = new HashSet<>(findAll(q, "(?:^|\\s)-repo:(\\S+)"));
        Set<String> excludedLabels = new HashSet<>(findAll(q, "-label:\"([^\"]+)\""));
        String before = find(q, "updated:<(\\S+)");
        List<ObjectNode> items = new ArrayList<>();
        repositories.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
        {
            Repository repository = entry.getValue();
            if (
                !entry.getKey().startsWith(organization + "/") || excludedRepositories.contains(entry.getKey())
                    || (q.contains("archived:false") && repository.archived) || (q.contains("is:public") && repository.isPrivate)
            ) {
                return;
            }
            repository.issues.forEach((number, real) ->
            {
                Issue issue = real;
                if (searchSnapshots.containsKey(number)) {
                    issue = snapshot(real, searchSnapshots.get(number));
                }
                boolean assigned = assignee.equals("*") ? !issue.assignees.isEmpty() : issue.assignees.contains(assignee);
                if (
                    !issue.state.equals("open") || issue.pullRequest || !assigned
                        || issue.labels.stream().anyMatch(l -> excludedLabels.contains(l))
                        || (before != null && issue.updatedAt.compareTo(before) >= 0)
                ) {
                    return;
                }
                ObjectNode item = issueJson(issue, number);
                item.put("repository_url", "https://api.github.com/repos/" + entry.getKey());
                items.add(item);
            });
        });
        if ("updated".equals(params.get("sort"))) {
            Comparator<ObjectNode> byDate = Comparator.comparing(n -> n.get("updated_at").asText());
            items.sort("asc".equals(params.get("order")) ? byDate : byDate.reversed());
        }
        int perPage = Integer.parseInt(params.getOrDefault("per_page", "30"));
        int page = Integer.parseInt(params.getOrDefault("page", "1"));
        ArrayNode pageItems = MAPPER.createArrayNode();
        items.stream().skip((long) (page - 1) * perPage).limit(perPage).forEach(pageItems::add);
        return MAPPER.createObjectNode().put("total_count", items.size()).put("incomplete_results", incompleteSearch).set("items", pageItems);
    }

    private JsonNode searchPullRequests(String q, Map<String, String> params) {
        String organization = find(q, "org:(\\S+)");
        Set<String> excludedLabels = new HashSet<>(findAll(q, "-label:\"([^\"]+)\""));
        Matcher range = Pattern.compile("updated:(\\S+)\\.\\.(\\S+)").matcher(q);
        boolean ranged = range.find();
        List<ObjectNode> items = new ArrayList<>();
        repositories.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
        {
            Repository repository = entry.getValue();
            if (
                !entry.getKey().startsWith(organization + "/") || (q.contains("archived:false") && repository.archived)
                    || (q.contains("is:public") && repository.isPrivate)
            ) {
                return;
            }
            repository.pullRequests.forEach((number, pr) ->
            {
                if (
                    !pr.state.equals("OPEN") || (q.contains("draft:false") && pr.draft)
                        || (q.contains("-author:app/dependabot") && pr.author.equals("dependabot[bot]"))
                        || pr.labels.stream().anyMatch(l -> excludedLabels.contains(l))
                        || (ranged && (pr.updatedAt.compareTo(range.group(1)) < 0 || pr.updatedAt.compareTo(range.group(2)) > 0))
                ) {
                    return;
                }
                items.add(
                    MAPPER.createObjectNode().put("number", number).put("updated_at", pr.updatedAt)
                        .put("repository_url", "https://api.github.com/repos/" + entry.getKey())
                );
            });
        });
        Comparator<ObjectNode> byDate = Comparator.comparing(n -> n.get("updated_at").asText());
        items.sort("asc".equals(params.get("order")) ? byDate : byDate.reversed());
        int perPage = Integer.parseInt(params.getOrDefault("per_page", "30"));
        int page = Integer.parseInt(params.getOrDefault("page", "1"));
        ArrayNode pageItems = MAPPER.createArrayNode();
        items.stream().skip((long) (page - 1) * perPage).limit(perPage).forEach(pageItems::add);
        return MAPPER.createObjectNode().put("total_count", items.size()).put("incomplete_results", false).set("items", pageItems);
    }

    private static ObjectNode activityJson(PullRequest pr) {
        ObjectNode node = MAPPER.createObjectNode().put("state", pr.state).put("isDraft", pr.draft).put("createdAt", pr.createdAt);
        node.putObject("author").put("__typename", pr.authorType).put("login", pr.author);
        ArrayNode labels = node.putObject("labels").putArray("nodes");
        pr.labels.forEach(l -> labels.add(MAPPER.createObjectNode().put("name", l)));
        ArrayNode assignees = node.putObject("assignees").putArray("nodes");
        pr.assignees.forEach(a -> assignees.add(MAPPER.createObjectNode().put("login", a)));
        ArrayNode requests = node.putObject("reviewRequests").putArray("nodes");
        pr.requestedReviewers.forEach(r -> requests.addObject().putObject("requestedReviewer").put("__typename", "User").put("login", r));
        ArrayNode reviews = node.putObject("reviews").putArray("nodes");
        pr.reviews.forEach(r ->
        {
            ObjectNode review = reviews.addObject().put("submittedAt", r.submittedAt());
            review.putObject("author").put("__typename", r.type()).put("login", r.login());
        });
        ObjectNode commit = node.putObject("commits").putArray("nodes").addObject().putObject("commit").put("committedDate", pr.commitAt);
        ObjectNode user = commit.putObject("author").putObject("user");
        user.put("login", pr.committer == null ? pr.author : pr.committer);
        ArrayNode comments = node.putObject("comments").putArray("nodes");
        pr.comments.forEach(c ->
        {
            ObjectNode comment = comments.addObject().put("createdAt", c.createdAt()).put("body", c.body());
            comment.putObject("author").put("__typename", c.type()).put("login", c.login());
        });
        ArrayNode events = node.putObject("timelineItems").putArray("nodes");
        pr.events.forEach(e ->
        {
            ObjectNode event = events.addObject().put("__typename", e.kind()).put("createdAt", e.createdAt());
            event.putObject("actor").put("login", e.actor());
        });
        return node;
    }

    private static Issue snapshot(Issue real, Map<String, Object> overrides) {
        Issue copy = new Issue();
        copy.state = (String) overrides.getOrDefault("state", real.state);
        copy.assignees = new ArrayList<>(castList(overrides.getOrDefault("assignees", real.assignees)));
        copy.labels = new ArrayList<>(castList(overrides.getOrDefault("labels", real.labels)));
        copy.updatedAt = (String) overrides.getOrDefault("updatedAt", real.updatedAt);
        copy.pullRequest = real.pullRequest;
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object value) {
        return (List<String>) value;
    }

    private JsonNode graphql(JsonNode body) {
        JsonNode variables = body.get("variables");
        String repository = variables.get("owner").asText() + "/" + variables.get("name").asText();
        int number = variables.get("number").asInt();
        if (body.get("query").asText().contains("timelineItems")) {
            if (failingIssueLookups.contains(number)) {
                return MAPPER.createObjectNode().put("__status", 500);
            }
            PullRequest pullRequest = repository(repository).pullRequests.get(number);
            ObjectNode data = MAPPER.createObjectNode();
            data.putObject("data").putObject("repository").set("pullRequest", pullRequest == null ? MAPPER.nullNode() : activityJson(pullRequest));
            return data;
        }
        if (!body.get("query").asText().contains("issue(number")) {
            JsonNode pullRequest = pullRequests.get(repository + "#" + number);
            ObjectNode data = MAPPER.createObjectNode();
            data.putObject("data").putObject("repository").set("pullRequest", pullRequest == null ? MAPPER.nullNode() : pullRequest);
            return data;
        }
        if (failingIssueLookups.contains(number)) {
            return MAPPER.createObjectNode().put("__status", 500);
        }
        Issue issue = repository(repository).issues.get(number);
        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode repo = data.putObject("data").putObject("repository");
        if (issue == null) {
            repo.putNull("issue");
            return data;
        }
        ObjectNode node = repo.putObject("issue");
        node.put("state", issue.state.toUpperCase()).put("updatedAt", issue.updatedAt);
        node.putObject("labels").putArray("nodes").addAll(issue.labels.stream().map(l -> (JsonNode) MAPPER.createObjectNode().put("name", l)).toList());
        node.putObject("assignees").putArray("nodes").addAll(issue.assignees.stream().map(a -> (JsonNode) MAPPER.createObjectNode().put("login", a)).toList());
        ArrayNode comments = node.putObject("comments").putArray("nodes");
        if (issue.lastComment != null) {
            comments.add(MAPPER.createObjectNode().put("body", issue.lastComment));
        }
        node.putObject("closedByPullRequestsReferences").put("totalCount", issue.openPullRequests);
        return data;
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> out = new HashMap<>();
        if (raw == null) {
            return out;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            out.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8), URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return out;
    }

    private static String find(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static List<String> findAll(String text, String regex) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile(regex).matcher(text);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }
}
