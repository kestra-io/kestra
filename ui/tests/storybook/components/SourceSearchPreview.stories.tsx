import SourceSearchPreview from "../../../src/components/flows/SourceSearchPreview.vue"
import type {Meta, StoryObj, Decorator} from "@storybook/vue3-vite"
import {vueRouter} from "storybook-vue3-router"
import {useFlowStore} from "../../../src/stores/flow"

// The preview's "open in …" buttons resolve named routes the global preview router
// does not declare, so each story needs them registered or useLink() throws.
const routes = [
    {path: "/", name: "home", component: {template: "<div />"}},
    {path: "/kv", name: "kv/list", component: {template: "<div />"}},
    {path: "/secrets", name: "secrets/list", component: {template: "<div />"}},
    {path: "/namespaces/edit/:id/files", name: "namespaces/update/files", component: {template: "<div />"}},
    {path: "/:pathMatch(.*)*", name: "catchAll", component: {template: "<div />"}},
]

const meta: Meta<typeof SourceSearchPreview> = {
    title: "flows/SourceSearchPreview",
    component: SourceSearchPreview,
    decorators: [
        (storyFn) => ({
            components: {story: storyFn},
            template: `<div style="height: 600px; width: 600px;"><story /></div>`,
        }),
        vueRouter(routes, {initialRoute: "/"}),
    ],
}

export default meta

const dailyEtlSelection = {type: "flows", namespace: "company.data", id: "daily-etl", line: 4, column: 8}
const kvSelection = {type: "kv", namespace: "company.data.ingestion", key: "landing-bucket-us-east-1"}

function story(overrides: Record<string, unknown> = {}): StoryObj<typeof SourceSearchPreview> {
    const props = {
        selection: null,
        query: "",
        caseSensitive: false,
        replaceMode: false,
        previewResponse: null,
        selectionSummary: null,
        readOnlyExcludedCount: 0,
        excludedFromReplaceCount: 0,
        kvEntry: null,
        ...overrides,
    }

    return {render: () => ({setup: () => () => <SourceSearchPreview {...props} />})}
}

/** Stubs flowStore.loadFlow so a story renders a given source without a backend. */
function withLoadedFlow(flow: Record<string, unknown> | null): Decorator {
    return (storyFn) => ({
        setup() {
            (useFlowStore() as any).loadFlow = () => flow === null ? new Promise(() => {}) : Promise.resolve(flow)
        },
        components: {story: storyFn},
        template: "<story />",
    })
}

export const NothingSelected = story()

export const FlowLoading: StoryObj<typeof SourceSearchPreview> = {
    ...story({selection: dailyEtlSelection}),
    decorators: [withLoadedFlow(null)],
}

export const FlowWithSource: StoryObj<typeof SourceSearchPreview> = {
    ...story({selection: dailyEtlSelection, query: "extract"}),
    decorators: [withLoadedFlow({
        id: "daily-etl",
        namespace: "company.data",
        source: "id: daily-etl\nnamespace: company.data\ntasks:\n  - id: extract\n    type: io.kestra.plugin.core.log.Log\n    message: Extracting data\n",
    })],
}

export const FlowReplaceModeDiff: StoryObj<typeof SourceSearchPreview> = {
    ...story({
        selection: {type: "flows", namespace: "company.team.data", id: "ingest-analytics-events", line: 5, column: 0},
        query: "analytics-prod",
        replaceMode: true,
        previewResponse: {
            totalMatches: 1,
            totalFlows: 1,
            editableFlowCount: 1,
            flows: [
                {
                    namespace: "company.team.data",
                    id: "ingest-analytics-events",
                    editable: true,
                    matches: [
                        {line: 5, before: "    projectId: analytics-prod", after: "    projectId: analytics-eu"},
                    ],
                },
            ],
        },
        selectionSummary: {selectedFlowCount: 1, selectedMatchCount: 1},
        readOnlyExcludedCount: 2,
        excludedFromReplaceCount: 5,
    }),
    decorators: [withLoadedFlow({
        id: "ingest-analytics-events",
        namespace: "company.team.data",
        source: "id: ingest-analytics-events\nnamespace: company.team.data\ntasks:\n  - id: extract\n    type: io.kestra.plugin.gcp.bigquery.Query\n    projectId: analytics-prod\n    sql: SELECT 1\n",
    })],
}

export const NamespaceFilePreview = story({
    selection: {type: "files", namespace: "company.data.ingestion", path: "scripts/us-east-1/extract.py"},
    query: "us-east-1",
})

export const KvKeyPreview = story({
    selection: kvSelection,
    query: "us-east-1",
    kvEntry: {key: "landing-bucket-us-east-1", creationDate: "2026-03-02T00:00:00Z", updateDate: "2026-08-07T00:00:00Z"},
})

export const SecretKeyPreview = story({
    selection: {type: "secrets", namespace: "company.data.ingestion", key: "aws-us-east-1-access-key"},
    query: "us-east-1",
})

export const DarkMode: StoryObj<typeof SourceSearchPreview> = {
    ...story({
        selection: kvSelection,
        query: "us-east-1",
        kvEntry: {key: "landing-bucket-us-east-1", updateDate: "2026-08-07T00:00:00Z"},
    }),
    parameters: {
        themes: {themeOverride: "dark"},
    },
}
