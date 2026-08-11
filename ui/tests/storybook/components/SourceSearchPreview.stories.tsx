import SourceSearchPreview from "../../../src/components/flows/SourceSearchPreview.vue"
import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {createI18n} from "vue-i18n"
import {createPinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"
import {useFlowStore} from "../../../src/stores/flow"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {
        en: {
            cancel: "Cancel",
            value: "Value",
            source_search: {
                confirm_bar_message: "Replace {matches} across {flows} editable flows. {skipped} read-only flows will be skipped.",
                diff_preview_aria: "Replacement diff preview",
                diff_preview_label: "diff preview · not yet applied",
                file_match_notice: "The file path matched. File content is not searched — open the file to search inside it.",
                kv_match_notice: "Only the key name was searched. Open the KV store to read the value.",
                line_label: "line {line}",
                match_count: "{count} match | {count} matches",
                meta_created: "Created",
                meta_expires: "Expires",
                meta_never: "Never",
                meta_updated: "Updated",
                open_in_editor: "Open in editor",
                open_in_kv: "Open in KV store",
                open_in_secrets: "Open in secrets",
                preview_empty: "Select a result to preview. Click a flow in the results list to see its source.",
                preview_error: "Failed to load flow source",
                replace_all: "Replace all",
                secret_match_notice: "Secret values are never read by search. Only key names are matched.",
                value_never_shown: "Never shown or searched",
                value_withheld: "Not shown",
            },
        },
    },
})

const pinia = createPinia()

const meta: Meta<typeof SourceSearchPreview> = {
    title: "flows/SourceSearchPreview",
    component: SourceSearchPreview,
    decorators: [
        (story) => ({
            components: {story},
            plugins: [i18n, KestraDesignSystem, pinia],
            template: `<div style="height: 600px; width: 600px;"><story /></div>`,
        }),
    ],
}

export default meta

function baseProps(overrides: Record<string, unknown> = {}) {
    return {
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
}

export const NothingSelected: StoryObj<typeof SourceSearchPreview> = {
    render: () => ({
        setup() {
            return () => <SourceSearchPreview {...baseProps()} />
        },
    }),
}

export const FlowLoading: StoryObj<typeof SourceSearchPreview> = {
    decorators: [
        (story) => ({
            setup() {
                const flowStore = useFlowStore()
                ;(flowStore as any).loadFlow = () => new Promise(() => {})
            },
            components: {story},
            template: `<story />`,
        }),
    ],
    render: () => ({
        setup() {
            return () => (
                <SourceSearchPreview
                    {...baseProps({selection: {type: "flows", namespace: "company.data", id: "daily-etl", line: 4, column: 8}})}
                />
            )
        },
    }),
}

export const FlowWithSource: StoryObj<typeof SourceSearchPreview> = {
    decorators: [
        (story) => ({
            setup() {
                const flowStore = useFlowStore()
                ;(flowStore as any).loadFlow = () =>
                    Promise.resolve({
                        id: "daily-etl",
                        namespace: "company.data",
                        source: "id: daily-etl\nnamespace: company.data\ntasks:\n  - id: extract\n    type: io.kestra.plugin.core.log.Log\n    message: Extracting data\n",
                    })
            },
            components: {story},
            template: `<story />`,
        }),
    ],
    render: () => ({
        setup() {
            return () => (
                <SourceSearchPreview
                    {...baseProps({selection: {type: "flows", namespace: "company.data", id: "daily-etl", line: 4, column: 8}, query: "extract"})}
                />
            )
        },
    }),
}

export const FlowReplaceModeDiff: StoryObj<typeof SourceSearchPreview> = {
    decorators: [
        (story) => ({
            setup() {
                const flowStore = useFlowStore()
                ;(flowStore as any).loadFlow = () =>
                    Promise.resolve({
                        id: "ingest-analytics-events",
                        namespace: "company.team.data",
                        source: "id: ingest-analytics-events\nnamespace: company.team.data\ntasks:\n  - id: extract\n    type: io.kestra.plugin.gcp.bigquery.Query\n    projectId: analytics-prod\n    sql: SELECT 1\n",
                    })
            },
            components: {story},
            template: `<story />`,
        }),
    ],
    render: () => ({
        setup() {
            const previewResponse = {
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
            }
            return () => (
                <SourceSearchPreview
                    {...baseProps({
                        selection: {type: "flows", namespace: "company.team.data", id: "ingest-analytics-events", line: 5, column: 0},
                        query: "analytics-prod",
                        replaceMode: true,
                        previewResponse,
                        selectionSummary: {selectedFlowCount: 1, selectedMatchCount: 1},
                        readOnlyExcludedCount: 2,
                        excludedFromReplaceCount: 5,
                    })}
                />
            )
        },
    }),
}

export const NamespaceFilePreview: StoryObj<typeof SourceSearchPreview> = {
    render: () => ({
        setup() {
            return () => (
                <SourceSearchPreview
                    {...baseProps({
                        selection: {type: "files", namespace: "company.data.ingestion", path: "scripts/us-east-1/extract.py"},
                        query: "us-east-1",
                    })}
                />
            )
        },
    }),
}

export const KvKeyPreview: StoryObj<typeof SourceSearchPreview> = {
    render: () => ({
        setup() {
            return () => (
                <SourceSearchPreview
                    {...baseProps({
                        selection: {type: "kv", namespace: "company.data.ingestion", key: "landing-bucket-us-east-1"},
                        query: "us-east-1",
                        kvEntry: {key: "landing-bucket-us-east-1", creationDate: "2026-03-02T00:00:00Z", updateDate: "2026-08-07T00:00:00Z"},
                    })}
                />
            )
        },
    }),
}

export const SecretKeyPreview: StoryObj<typeof SourceSearchPreview> = {
    render: () => ({
        setup() {
            return () => (
                <SourceSearchPreview
                    {...baseProps({
                        selection: {type: "secrets", namespace: "company.data.ingestion", key: "aws-us-east-1-access-key"},
                        query: "us-east-1",
                    })}
                />
            )
        },
    }),
}

export const DarkMode: StoryObj<typeof SourceSearchPreview> = {
    render: () => ({
        setup() {
            return () => (
                <SourceSearchPreview
                    {...baseProps({
                        selection: {type: "kv", namespace: "company.data.ingestion", key: "landing-bucket-us-east-1"},
                        query: "us-east-1",
                        kvEntry: {key: "landing-bucket-us-east-1", updateDate: "2026-08-07T00:00:00Z"},
                    })}
                />
            )
        },
    }),
    parameters: {
        themes: {themeOverride: "dark"},
    },
}
