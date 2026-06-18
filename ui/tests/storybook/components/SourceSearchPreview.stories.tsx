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
            source_search: {
                preview_empty: "Select a result to preview. Click a flow in the results list to see its source.",
                preview_error: "Failed to load flow source",
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
                    selected={{namespace: "company.data", id: "daily-etl", matchIndex: 0}}
                    query=""
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
                    selected={{namespace: "company.data", id: "missing-flow", matchIndex: 0}}
                    query=""
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
                    selected={{namespace: "company.data", id: "daily-etl", matchIndex: 0}}
                    query="extract"
                />
            )
        },
    }),
}

export const WithSourceSecondMatch: StoryObj<typeof SourceSearchPreview> = {
    decorators: [
        (story) => ({
            setup() {
                const flowStore = useFlowStore()
                ;(flowStore as any).loadFlow = () =>
                    Promise.resolve({
                        id: "daily-etl",
                        namespace: "company.data",
                        source: "id: daily-etl\nnamespace: company.data\ntasks:\n  - id: extract\n    type: io.kestra.plugin.core.log.Log\n    message: Extracting data for extract job\n",
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
                    selected={{namespace: "company.data", id: "daily-etl", matchIndex: 1}}
                    query="extract"
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
                />
            )
        },
    }),
    parameters: {
        themes: {themeOverride: "dark"},
    },
}
