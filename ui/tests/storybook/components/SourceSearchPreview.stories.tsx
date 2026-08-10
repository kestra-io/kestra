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
            source_search: {
                confirm_bar_message: "Replace {matches} across {flows} editable flows. {skipped} read-only flows will be skipped.",
                diff_preview_aria: "Replacement diff preview",
                diff_preview_label: "diff preview · not yet applied",
                line_label: "line {line}",
                match_count: "{count} match | {count} matches",
                open_in_editor: "Open in editor",
                preview_empty: "Select a result to preview. Click a flow in the results list to see its source.",
                preview_error: "Failed to load flow source",
                replace_all: "Replace all",
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

export const NothingSelected: StoryObj<typeof SourceSearchPreview> = {
    render: () => ({
        setup() {
            return () => (
                <SourceSearchPreview
                    selected={null}
                    query=""
                    replaceMode={false}
                    previewResponse={null}
                    selectionSummary={null}
                    readOnlyExcludedCount={0}
                />
            )
        },
    }),
}

export const Loading: StoryObj<typeof SourceSearchPreview> = {
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
                    selected={{namespace: "company.data", id: "daily-etl", line: 4}}
                    query=""
                    replaceMode={false}
                    previewResponse={null}
                    selectionSummary={null}
                    readOnlyExcludedCount={0}
                />
            )
        },
    }),
}

export const ErrorState: StoryObj<typeof SourceSearchPreview> = {
    decorators: [
        (story) => ({
            setup() {
                const flowStore = useFlowStore()
                ;(flowStore as any).loadFlow = () => Promise.reject(new Error("404 Not Found"))
            },
            components: {story},
            template: `<story />`,
        }),
    ],
    render: () => ({
        setup() {
            return () => (
                <SourceSearchPreview
                    selected={{namespace: "company.data", id: "missing-flow", line: 4}}
                    query=""
                    replaceMode={false}
                    previewResponse={null}
                    selectionSummary={null}
                    readOnlyExcludedCount={0}
                />
            )
        },
    }),
}

export const WithSource: StoryObj<typeof SourceSearchPreview> = {
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
                    selected={{namespace: "company.data", id: "daily-etl", line: 4}}
                    query="extract"
                    replaceMode={false}
                    previewResponse={null}
                    selectionSummary={null}
                    readOnlyExcludedCount={0}
                />
            )
        },
    }),
}

export const ReplaceModeDiffPreview: StoryObj<typeof SourceSearchPreview> = {
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
                    selected={{namespace: "company.team.data", id: "ingest-analytics-events", line: 5}}
                    query="analytics-prod"
                    replaceMode={true}
                    previewResponse={previewResponse}
                    selectionSummary={{selectedFlowCount: 1, selectedMatchCount: 1}}
                    readOnlyExcludedCount={2}
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
                    selected={null}
                    query=""
                    replaceMode={false}
                    previewResponse={null}
                    selectionSummary={null}
                    readOnlyExcludedCount={0}
                />
            )
        },
    }),
    parameters: {
        themes: {themeOverride: "dark"},
    },
}
