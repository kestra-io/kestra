import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"
import ImportYaml from "../../../../src/components/flows/create/ImportYaml.vue"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {
        en: {
            "new_flow_landing.import.title": "Import YAML",
            "new_flow_landing.import.back": "Back",
            "new_flow_landing.import.paste_label": "Paste YAML",
            "new_flow_landing.import.upload_label": "Or upload a file",
            "new_flow_landing.import.upload_button": "Upload .yml / .yaml",
            "new_flow_landing.import.upload_tip": "Accepts .yml and .yaml files.",
            "new_flow_landing.import.submit": "Import flow",
            "new_flow_landing.import.read_error": "Could not read the file.",
            "new_flow_landing.import.error.empty": "YAML content is empty.",
            "new_flow_landing.import.error.invalid_mapping": "Invalid flow YAML: expected a key-value mapping, not a list or scalar.",
            "new_flow_landing.import.error.parse_error": "Could not parse YAML.",
        },
    },
})

const meta: Meta<typeof ImportYaml> = {
    title: "Flows/Create/ImportYaml",
    component: ImportYaml,
    decorators: [
        (story) => ({
            components: {story},
            plugins: [i18n, KestraDesignSystem],
            template: `<div style="min-height: 100vh; background: var(--ks-bg-base);"><story /></div>`,
        }),
    ],
    parameters: {
        layout: "fullscreen",
    },
}

export default meta

export const Default: StoryObj<typeof ImportYaml> = {
    name: "Default (empty)",
}
