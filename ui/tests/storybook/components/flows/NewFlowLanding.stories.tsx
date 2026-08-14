import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {createI18n} from "vue-i18n"
import {createPinia} from "pinia"
import {createMemoryHistory} from "vue-router"
import {vueRouter} from "storybook-vue3-router"
import KestraDesignSystem from "@kestra-io/design-system"
import NewFlowLanding from "../../../../src/components/flows/create/NewFlowLanding.vue"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {
        en: {
            "new_flow_landing.title": "Create a new flow",
            "new_flow_landing.subtitle": "Start from a blank canvas, explore blueprints, or import an existing YAML.",
            "new_flow_landing.blank.title": "Blank flow",
            "new_flow_landing.blank.subtitle": "Start with a hello-world starter and build from scratch.",
            "new_flow_landing.blank.id_label": "Flow id",
            "new_flow_landing.blank.id_placeholder": "my-flow",
            "new_flow_landing.blank.namespace_placeholder": "Select a namespace",
            "new_flow_landing.blank.open_editor": "Open editor",
            "new_flow_landing.blank.namespaces_error": "Could not load namespaces. Type a namespace manually.",
            "new_flow_landing.blueprints.title": "Browse blueprints",
            "new_flow_landing.blueprints.subtitle": "Pick a ready-made flow from the community catalog.",
            "new_flow_landing.system.title": "Create a system flow",
            "new_flow_landing.system.subtitle": "Build an alert or automation flow for your platform.",
            "new_flow_landing.system.badge": "SYSTEM",
            "new_flow_landing.import.title": "Import YAML",
            "new_flow_landing.import.subtitle": "Paste or upload an existing flow definition.",
            namespace: "namespace",
        },
    },
})

const pinia = createPinia()

const STORY_ROUTES = [
    {path: "/", name: "home", component: {template: "<div />"}},
    {path: "/blueprints/:kind/:tab", name: "blueprints", component: {template: "<div />"}},
    {path: "/namespaces/:id", name: "namespaces/update", component: {template: "<div />"}},
]

const meta: Meta<typeof NewFlowLanding> = {
    title: "Flows/Create/NewFlowLanding",
    component: NewFlowLanding,
    decorators: [
        (story) => ({
            components: {story},
            plugins: [i18n, pinia, KestraDesignSystem],
            template: `<div style="min-height: 100vh; background: var(--ks-bg-base);"><story /></div>`,
        }),
        // The landing renders router-links to these named routes; the global
        // preview router only registers home/about, and named-route resolution
        // has no catch-all fallback.
        vueRouter(
            STORY_ROUTES,
            {vueRouterOptions: {history: createMemoryHistory(), routes: STORY_ROUTES}},
        ),
    ],
    parameters: {
        layout: "fullscreen",
    },
}

export default meta

export const Default: StoryObj<typeof NewFlowLanding> = {
    name: "Default (empty form)",
}
