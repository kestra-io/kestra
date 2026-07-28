import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsEditor from "../../../src/components/Form/KsEditor.vue"

const meta: Meta<typeof KsEditor> = {
    title: "Components/Form/KsEditor",
    component: KsEditor,
    tags: ["autodocs"],
    argTypes: {
        lang: {control: "select", options: ["yaml", "json", "python", "javascript", "typescript", "shell", "markdown", "plaintext", "yaml-pebble"]},
        schemaType: {control: "select", options: [undefined, "flow", "dashboard", "app", "testsuites", "section"]},
        theme: {control: "select", options: ["dark", "light", "vs"]},
        inline: {control: "boolean"},
        readOnly: {control: "boolean"},
        navbar: {control: "boolean"},
        options: {control: "object"},
    },
    parameters: {
        docs: {description: {component: "KsEditor is the unified Monaco-based code editor for the Kestra UI. It replaces the legacy `Editor.vue` and `MonacoEditor.vue` wrappers and exposes a single prop surface for every use-case (yaml/json/python/etc., inline single-line, diff, flow schema autocompletion, dashboard, app, testsuites, plaintext file preview, ...)."}},
    },
}
export default meta
type Story = StoryObj<typeof KsEditor>

const YAML_SAMPLE = `id: hello-world
namespace: company.team
tasks:
  - id: hello
    type: io.kestra.plugin.core.log.Log
    message: "Hello, {{ flow.id }}!"
`

const JSON_SAMPLE = `{
  "name": "kestra",
  "version": "1.0.0",
  "tags": ["{{ env }}", "{{ flow.id }}"]
}
`

export const Default: Story = {
    render: (args) => ({
        components: {KsEditor},
        setup() {
            const value = ref(YAML_SAMPLE)
            return {args, value}
        },
        template: "<div style=\"padding:24px;height:360px\"><ks-editor v-model=\"value\" v-bind=\"args\" /></div>",
    }),
    args: {lang: "yaml", theme: "dark"},
}

export const FlowSchema: Story = {
    render: () => ({
        components: {KsEditor},
        setup() { return {value: ref(YAML_SAMPLE)} },
        template: "<div style=\"padding:24px;height:420px\"><ks-editor v-model=\"value\" lang=\"yaml\" schemaType=\"flow\" /></div>",
    }),
    parameters: {docs: {description: {story: "With `schemaType=flow`, pebble `{{ }}` highlighting is auto-enabled and duplicate task-id markers are added on parse errors. Autocompletion is wired by the consumer via `configureLanguage` prop."}}},
}

export const Inline: Story = {
    render: () => ({
        components: {KsEditor},
        setup() { return {value: ref("hello = {{ flow.id }}")} },
        template: "<div style=\"padding:24px;width:480px\"><ks-editor v-model=\"value\" lang=\"yaml-pebble\" :inline=\"true\" /></div>",
    }),
    parameters: {docs: {description: {story: "Single-line variant — navbar suppressed, height clamped to one line, no minimap, no line numbers."}}},
}

export const Diff: Story = {
    render: () => ({
        components: {KsEditor},
        setup() {
            const original = ref(YAML_SAMPLE)
            const modified = ref(YAML_SAMPLE.replace("hello", "world"))
            return {original, modified}
        },
        template: "<div style=\"padding:24px;height:420px\"><ks-editor v-model=\"modified\" :original=\"original\" lang=\"yaml\" /></div>",
    }),
    parameters: {docs: {description: {story: "Passing `original` mounts the diff editor. Toggle `options.diffSideBySide` for inline vs side-by-side."}}},
}

export const Json: Story = {
    render: () => ({
        components: {KsEditor},
        setup() { return {value: ref(JSON_SAMPLE)} },
        template: "<div style=\"padding:24px;height:360px\"><ks-editor v-model=\"value\" lang=\"json\" /></div>",
    }),
    parameters: {docs: {description: {story: "JSON mode. Pebble highlighting is OFF by default (no flow-class schemaType)."}}},
}

export const ReadOnly: Story = {
    render: () => ({
        components: {KsEditor},
        setup() { return {value: ref(YAML_SAMPLE)} },
        template: "<div style=\"padding:24px;height:360px\"><ks-editor v-model=\"value\" lang=\"yaml\" :readOnly=\"true\" /></div>",
    }),
}

export const LightTheme: Story = {
    render: () => ({
        components: {KsEditor},
        setup() { return {value: ref(YAML_SAMPLE)} },
        template: "<div style=\"padding:24px;height:360px;background:#fff\"><ks-editor v-model=\"value\" lang=\"yaml\" theme=\"light\" /></div>",
    }),
}

export const Plaintext: Story = {
    render: () => ({
        components: {KsEditor},
        setup() { return {value: ref("Plain text with {{ pebble }} that should NOT highlight (no schemaType).")} },
        template: "<div style=\"padding:24px;height:200px\"><ks-editor v-model=\"value\" lang=\"plaintext\" /></div>",
    }),
    parameters: {docs: {description: {story: "Plaintext (e.g. file preview, audit log content). Pebble highlight off by default — flow-only feature."}}},
}

export const WithPlaceholder: Story = {
    render: () => ({
        components: {KsEditor},
        setup() { return {value: ref("")} },
        template: "<div style=\"padding:24px;height:200px\"><ks-editor v-model=\"value\" lang=\"yaml\" placeholder=\"Type your flow YAML here...\" /></div>",
    }),
}

export const WithLabel: Story = {
    render: () => ({
        components: {KsEditor},
        setup() { return {value: ref(YAML_SAMPLE)} },
        template: "<div style=\"padding:24px;height:360px\"><ks-editor v-model=\"value\" lang=\"yaml\" label=\"Flow YAML\" /></div>",
    }),
}
