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
                match_count: "{count} match | {count} matches",
                open_flow: "Open flow",
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
        model: {namespace: "company.data", id: "daily-etl"},
        fragments: [
            "tasks:\n  - id: [mark]extract[/mark]\n    type: io.kestra.plugin.core.log.Log",
        ],
    },
]

const multipleResults = [
    {
        model: {namespace: "company.data", id: "daily-etl"},
        fragments: [
            "tasks:\n  - id: [mark]extract[/mark]\n    type: io.kestra.plugin.core.log.Log",
            "  - id: load\n    script: [mark]extract[/mark]Data()",
        ],
    },
    {
        model: {namespace: "company.analytics", id: "weekly-report"},
        fragments: [
            "description: Weekly [mark]extract[/mark] and summarize",
        ],
    },
    {
        model: {namespace: "system", id: "health-check"},
        fragments: [
            "id: [mark]health[/mark]-check\nnamespace: system",
        ],
    },
]

export const Empty: StoryObj<typeof SourceSearchResults> = {
    render: () => ({
        setup() {
            return () => (
                <SourceSearchResults
                    results={undefined}
                    selectedKey={null}
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
                    selectedKey="company.data.daily-etl#0"
                />
            )
        },
    }),
}

export const WithSelectedFragment: StoryObj<typeof SourceSearchResults> = {
    render: () => ({
        setup() {
            return () => (
                <SourceSearchResults
                    results={multipleResults}
                    selectedKey="company.data.daily-etl#1"
                />
            )
        },
    }),
}

export const LongFragments: StoryObj<typeof SourceSearchResults> = {
    render: () => ({
        setup() {
            const longResults = [
                {
                    model: {namespace: "very.long.namespace.with.many.parts", id: "a-very-long-flow-identifier-that-goes-on-and-on"},
                    fragments: [
                        "This is a very long fragment that contains the [mark]search term[/mark] somewhere in the middle of a very long line that should demonstrate text wrapping behavior in the UI",
                        "Another long fragment with [mark]search term[/mark] at the start and then continues with a lot more content",
                    ],
                },
            ]
            return () => (
                <SourceSearchResults
                    results={longResults}
                    selectedKey={null}
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
                    selectedKey="company.analytics.weekly-report#0"
                />
            )
        },
    }),
    parameters: {
        themes: {themeOverride: "dark"},
    },
}
