import SourceSearchPreview from "../../../src/components/flows/SourceSearchPreview.vue"
import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {createI18n} from "vue-i18n"
import {createPinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {
        en: {
            search: {
                preview_empty_title: "Select a result to preview",
                preview_empty_description: "Click on a flow in the results list to preview its source here",
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
