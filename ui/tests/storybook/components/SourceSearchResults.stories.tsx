import SourceSearchResults from "../../../src/components/flows/SourceSearchResults.vue"
import type {Meta, StoryObj} from "@storybook/vue3-vite"
import type {SearchResourceType, SearchStatus} from "../../../src/utils/crossResourceSearch"

const meta: Meta<typeof SourceSearchResults> = {
    title: "flows/SourceSearchResults",
    component: SourceSearchResults,
    decorators: [
        (storyFn) => ({
            components: {story: storyFn},
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

const allTypesFound = {
    flowsResults,
    filesStatus: "done",
    filesNamespaces: [filesNamespaces[0]],
    kvStatus: "done",
    kvGroups,
    secretsStatus: "done",
    secretsGroups,
}

function story(overrides: Record<string, unknown> = {}): StoryObj<typeof SourceSearchResults> {
    const props = {
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

    return {render: () => ({setup: () => () => <SourceSearchResults {...props} />})}
}

export const Empty = story()

export const AllFourTypes = story(allTypesFound)

export const ProgressiveFilesWithFailure = story({
    selectedTypes: ["files"],
    filesStatus: "counting",
    filesNamespaces,
})

export const ReplaceModeWithSelection = story({
    ...allTypesFound,
    replaceMode: true,
    selectedKey: "flows:company.data.daily-etl#4:8",
    selectedMatchKeys: new Set([
        "flows:company.data.daily-etl#4:8",
        "flows:company.data.daily-etl#12:12",
    ]),
})

export const SecretsOnlySelected = story({
    selectedTypes: ["secrets"],
    secretsStatus: "done",
    secretsGroups,
})

export const DarkMode: StoryObj<typeof SourceSearchResults> = {
    ...story(allTypesFound),
    parameters: {
        themes: {themeOverride: "dark"},
    },
}
