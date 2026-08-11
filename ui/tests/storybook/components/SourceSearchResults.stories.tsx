import SourceSearchResults from "../../../src/components/flows/SourceSearchResults.vue"
import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"
import type {SearchResourceType, SearchStatus} from "../../../src/utils/crossResourceSearch"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {
        en: {
            source_search: {
                cannot_select_read_only: "Cannot select — you lack edit permission on {namespace} / {id}",
                count_flows: "{count} flow | {count} flows",
                count_keys: "{count} key | {count} keys",
                count_namespaces: "{count} namespace | {count} namespaces",
                count_paths: "{count} path | {count} paths",
                match_count: "{count} match | {count} matches",
                namespace_search_failed: "{namespace} couldn't be searched",
                namespace_search_failed_detail: "Request failed. The other namespaces are unaffected.",
                open_flow: "Open flow",
                read_only: "Read-only",
                read_only_tooltip: "You have read access but not edit access on this namespace",
                replace_all_in_flow: "Replace all in flow",
                replace_this: "Replace",
                replace_this_match: "Replace this match",
                retry_namespace: "Retry this namespace",
                searching_namespace: "Searching {namespace}",
                select_all_in_flow: "Select all matches in {namespace} / {id}",
                select_match: "Select match on line {line}",
                tag_keys_only_never: "Keys only — values never shown or searched",
                tag_keys_only_values: "Keys only — values are not searched",
                tag_paths_only: "Paths only — file content is not searched",
                tag_search_only: "Search only — not replaced",
                tag_source_code: "Source code",
                type_files: "Namespace files",
                type_flows: "Flows",
                type_kv: "KV keys",
                type_meta: "{matches} · {resources}",
                type_secrets: "Secret keys",
            },
        },
    },
})

const meta: Meta<typeof SourceSearchResults> = {
    title: "flows/SourceSearchResults",
    component: SourceSearchResults,
    decorators: [
        (story) => ({
            components: {story},
            plugins: [i18n, KestraDesignSystem],
            template: `<div style="height: 600px; width: 480px;"><story /></div>`,
        }),
    ],
}

export default meta

const flowsResults = [
    {
        namespace: "company.data",
        id: "daily-etl",
        editable: true,
        matches: [
            {line: 4, column: 8, snippet: "  - id: [mark]extract[/mark]"},
            {line: 12, column: 12, snippet: "    script: [mark]extract[/mark]Data()"},
        ],
    },
    {
        namespace: "prod.payments",
        id: "reconcile-ledger",
        editable: false,
        matches: [
            {line: 9, column: 15, snippet: "    projectId: [mark]analytics[/mark]-prod"},
        ],
    },
]

const filesNamespaces = [
    {namespace: "company.data.ingestion", status: "done" as const, paths: ["scripts/us-east-1/extract.py", "configs/us-east-1.yaml"]},
    {namespace: "company.platform", status: "failed" as const, paths: [], errorMessage: "Request timed out after 30s."},
    {namespace: "company.ml", status: "pending" as const, paths: []},
]

const kvGroups = [
    {namespace: "company.data.ingestion", matches: [{key: "landing-bucket-us-east-1", updateDate: "2026-01-01T00:00:00Z"}]},
    {namespace: "company.platform", matches: [{key: "warehouse-endpoint-us-east-1"}]},
]

const secretsGroups = [
    {namespace: "company.data.ingestion", matches: [{key: "aws-us-east-1-access-key"}, {key: "aws-us-east-1-secret-key"}]},
]

function baseProps(overrides: Record<string, unknown> = {}) {
    return {
        query: "us-east-1",
        caseSensitive: false,
        selectedTypes: ["flows", "files", "kv", "secrets"] as SearchResourceType[],
        flowsStatus: "done" as SearchStatus,
        flowsResults: [],
        filesStatus: "idle" as SearchStatus,
        filesNamespaces: [],
        kvStatus: "idle" as SearchStatus,
        kvGroups: [],
        secretsStatus: "idle" as SearchStatus,
        secretsGroups: [],
        selectedKey: null,
        replaceMode: false,
        selectedMatchKeys: new Set<string>(),
        ...overrides,
    } as InstanceType<typeof SourceSearchResults>["$props"]
}

export const Empty: StoryObj<typeof SourceSearchResults> = {
    render: () => ({
        setup() {
            return () => <SourceSearchResults {...baseProps()} />
        },
    }),
}

export const AllFourTypes: StoryObj<typeof SourceSearchResults> = {
    render: () => ({
        setup() {
            return () => (
                <SourceSearchResults
                    {...baseProps({
                        flowsResults,
                        filesStatus: "done",
                        filesNamespaces: [filesNamespaces[0]],
                        kvStatus: "done",
                        kvGroups,
                        secretsStatus: "done",
                        secretsGroups,
                    })}
                />
            )
        },
    }),
}

export const ProgressiveFilesWithFailure: StoryObj<typeof SourceSearchResults> = {
    render: () => ({
        setup() {
            return () => (
                <SourceSearchResults
                    {...baseProps({
                        selectedTypes: ["files"],
                        filesStatus: "counting",
                        filesNamespaces,
                    })}
                />
            )
        },
    }),
}

export const ReplaceModeWithSelection: StoryObj<typeof SourceSearchResults> = {
    render: () => ({
        setup() {
            const selectedMatchKeys = new Set([
                "flows:company.data.daily-etl#4:8",
                "flows:company.data.daily-etl#12:12",
            ])
            return () => (
                <SourceSearchResults
                    {...baseProps({
                        flowsResults,
                        filesStatus: "done",
                        filesNamespaces: [filesNamespaces[0]],
                        kvStatus: "done",
                        kvGroups,
                        replaceMode: true,
                        selectedKey: "flows:company.data.daily-etl#4:8",
                        selectedMatchKeys,
                    })}
                />
            )
        },
    }),
}

export const SecretsOnlySelected: StoryObj<typeof SourceSearchResults> = {
    render: () => ({
        setup() {
            return () => (
                <SourceSearchResults
                    {...baseProps({
                        selectedTypes: ["secrets"],
                        secretsStatus: "done",
                        secretsGroups,
                    })}
                />
            )
        },
    }),
}

export const DarkMode: StoryObj<typeof SourceSearchResults> = {
    render: () => ({
        setup() {
            return () => (
                <SourceSearchResults
                    {...baseProps({
                        flowsResults,
                        filesStatus: "done",
                        filesNamespaces: [filesNamespaces[0]],
                        kvStatus: "done",
                        kvGroups,
                        secretsStatus: "done",
                        secretsGroups,
                    })}
                />
            )
        },
    }),
    parameters: {
        themes: {themeOverride: "dark"},
    },
}
