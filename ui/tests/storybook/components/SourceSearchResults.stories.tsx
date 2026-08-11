import SourceSearchResults from "../../../src/components/flows/SourceSearchResults.vue"
import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {
        en: {
            source_search: {
                cannot_select_read_only: "Cannot select — you lack edit permission on {namespace} / {id}",
                match_count: "{count} match | {count} matches",
                open_flow: "Open flow",
                read_only: "Read-only",
                read_only_tooltip: "You have read access but not edit access on this namespace",
                replace_all_in_flow: "Replace all in flow",
                replace_this_match: "Replace this match",
                select_all_in_flow: "Select all matches in {namespace} / {id}",
                select_match: "Select match on line {line}",
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
            template: `<div style="height: 600px; width: 400px;"><story /></div>`,
        }),
    ],
}

export default meta

const singleResult = [
    {
        namespace: "company.data",
        id: "daily-etl",
        editable: true,
        matches: [
            {line: 4, column: 8, snippet: "  - id: [mark]extract[/mark]"},
        ],
    },
]

const multipleResults = [
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
        namespace: "company.analytics",
        id: "weekly-report",
        editable: true,
        matches: [
            {line: 3, column: 20, snippet: "description: Weekly [mark]extract[/mark] and summarize"},
        ],
    },
    {
        namespace: "company.data",
        id: "warehouse-sync",
        editable: true,
        matches: [
            {line: 30, column: 0, snippet: "    type: io.kestra.plugin.gcp.bigquery.Query"},
            {line: 34, column: 0, snippet: "    serviceAccount: secret('GCP_SERVICE_ACCOUNT')"},
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

export const Empty: StoryObj<typeof SourceSearchResults> = {
    render: () => ({
        setup() {
            return () => (
                <SourceSearchResults
                    results={[]}
                    selectedKey={null}
                    replaceMode={false}
                    selectedMatchKeys={new Set()}
                />
            )
        },
    }),
}

export const SingleGroup: StoryObj<typeof SourceSearchResults> = {
    render: () => ({
        setup() {
            return () => (
                <SourceSearchResults
                    results={singleResult}
                    selectedKey={null}
                    replaceMode={false}
                    selectedMatchKeys={new Set()}
                />
            )
        },
    }),
}

export const ManyGroups: StoryObj<typeof SourceSearchResults> = {
    render: () => ({
        setup() {
            return () => (
                <SourceSearchResults
                    results={multipleResults}
                    selectedKey={null}
                    replaceMode={false}
                    selectedMatchKeys={new Set()}
                />
            )
        },
    }),
}

export const WithSelectedGroup: StoryObj<typeof SourceSearchResults> = {
    render: () => ({
        setup() {
            return () => (
                <SourceSearchResults
                    results={multipleResults}
                    selectedKey="company.data.daily-etl#4"
                    replaceMode={false}
                    selectedMatchKeys={new Set()}
                />
            )
        },
    }),
}

export const WithSelectedMatch: StoryObj<typeof SourceSearchResults> = {
    render: () => ({
        setup() {
            return () => (
                <SourceSearchResults
                    results={multipleResults}
                    selectedKey="company.data.daily-etl#12"
                    replaceMode={false}
                    selectedMatchKeys={new Set()}
                />
            )
        },
    }),
}

export const ReplaceModeWithSelection: StoryObj<typeof SourceSearchResults> = {
    render: () => ({
        setup() {
            const selectedMatchKeys = new Set([
                "company.data.daily-etl#4",
                "company.data.daily-etl#12",
                "company.analytics.weekly-report#3",
                "company.data.warehouse-sync#30",
            ])
            return () => (
                <SourceSearchResults
                    results={multipleResults}
                    selectedKey="company.data.daily-etl#4"
                    replaceMode={true}
                    selectedMatchKeys={selectedMatchKeys}
                />
            )
        },
    }),
}

export const LongContent: StoryObj<typeof SourceSearchResults> = {
    render: () => ({
        setup() {
            const longResults = [
                {
                    namespace: "very.long.namespace.with.many.parts",
                    id: "a-very-long-flow-identifier-that-goes-on-and-on",
                    editable: true,
                    matches: [
                        {line: 42, column: 43, snippet: "This is a very long line that contains the [mark]search term[/mark] somewhere in the middle of a very long line that should demonstrate text wrapping behavior in the UI"},
                        {line: 87, column: 23, snippet: "Another long line with [mark]search term[/mark] at the start and then continues with a lot more content"},
                    ],
                },
            ]
            return () => (
                <SourceSearchResults
                    results={longResults}
                    selectedKey={null}
                    replaceMode={false}
                    selectedMatchKeys={new Set()}
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
                    results={multipleResults}
                    selectedKey="company.analytics.weekly-report#3"
                    replaceMode={true}
                    selectedMatchKeys={new Set(["company.analytics.weekly-report#3"])}
                />
            )
        },
    }),
    parameters: {
        themes: {themeOverride: "dark"},
    },
}
