import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref, watchEffect} from "vue"
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

type EditorHandle = {
    focus: () => void
    destroy: () => void
    highlightLinesRange: (range: {start: number; end: number}) => void
    clearLinesRangeHighlights: () => void
    addContentWidget: (widget: {id: string; position: {lineNumber: number; column: number}; height: number; right: string}) => Promise<void>
    removeContentWidget: (id: string) => void
    monaco: unknown
    getEditor: () => {getValue?: () => string; getOriginalEditor?: () => unknown} | undefined
}

const handles: Record<string, EditorHandle | undefined> = {}

function handleStory(key: string, template: string, value = YAML_SAMPLE) {
    return () => ({
        components: {KsEditor},
        setup() {
            const editor = ref<EditorHandle>()
            watchEffect(() => { handles[key] = editor.value })
            return {value: ref(value), editor}
        },
        template,
    })
}

/**
 * Waits for a story's editor to mount. Monaco is loaded on demand, so a fixed
 * delay is not enough on a cold module cache.
 */
async function settled(key: string): Promise<void> {
    const deadline = Date.now() + 15000
    while (Date.now() < deadline) {
        await new Promise(resolve => setTimeout(resolve, 50))
        if (handles[key]?.getEditor() !== undefined) return
    }
    throw new Error(`the '${key}' editor did not mount within 15s`)
}

async function toleratingMonacoCancelledOnDispose(run: () => void): Promise<void> {
    const swallowCancelled = (event: PromiseRejectionEvent) => {
        const reason = event.reason as {name?: string} | undefined
        if ((reason?.name ?? String(event.reason)).includes("Canceled")) event.preventDefault()
    }
    window.addEventListener("unhandledrejection", swallowCancelled)
    try {
        run()
        await new Promise(resolve => setTimeout(resolve, 100))
    } finally {
        window.removeEventListener("unhandledrejection", swallowCancelled)
    }
}

export const ExposedApi: Story = {
    render: handleStory("api", "<div style=\"padding:24px;height:300px\"><ks-editor ref=\"editor\" v-model=\"value\" lang=\"yaml\" /></div>"),
    play: async () => {
        await settled("api")
        const api = handles["api"]!

        for (const method of ["focus", "destroy", "highlightLinesRange", "clearLinesRangeHighlights", "addContentWidget", "removeContentWidget", "getEditor"] as const) {
            if (typeof api[method] !== "function") throw new Error(`KsEditor no longer exposes ${method}`)
        }
        if (!api.monaco) throw new Error("KsEditor no longer exposes monaco")

        const editor = api.getEditor()
        if (!editor) throw new Error("getEditor() returned nothing after mount")
        if (editor.getValue?.() !== YAML_SAMPLE) throw new Error("the mounted editor does not hold the bound model value")
    },
}

export const HighlightsAndDestroy: Story = {
    render: handleStory("lifecycle", "<div style=\"padding:24px;height:300px\"><ks-editor ref=\"editor\" v-model=\"value\" lang=\"yaml\" /></div>"),
    play: async () => {
        await settled("lifecycle")
        const api = handles["lifecycle"]!

        api.highlightLinesRange({start: 1, end: 2})
        api.clearLinesRangeHighlights()
        api.focus()

        await toleratingMonacoCancelledOnDispose(() => {
            api.destroy()
            api.destroy()
        })
        if (api.getEditor() !== undefined) throw new Error("getEditor() still resolves after destroy()")
    },
}

export const DiffResolvesDiffEditor: Story = {
    render: handleStory("diff", "<div style=\"padding:24px;height:300px\"><ks-editor ref=\"editor\" v-model=\"value\" original=\"id: before\" lang=\"yaml\" /></div>"),
    play: async () => {
        await settled("diff")
        const editor = handles["diff"]!.getEditor()
        if (typeof editor?.getOriginalEditor !== "function") throw new Error("an original prop no longer resolves a diff editor")
    },
}

export const InstallsWindowHelpers: Story = {
    render: handleStory("helpers", "<div style=\"padding:24px;height:300px\"><ks-editor ref=\"editor\" v-model=\"value\" lang=\"yaml\" /></div>"),
    play: async () => {
        await settled("helpers")
        const w = window as unknown as Record<string, unknown>
        for (const helper of ["pasteToEditor", "clearEditor", "acceptSuggestion", "nextSuggestion"]) {
            if (typeof w[helper] !== "function") throw new Error(`window.${helper} is no longer installed on mount`)
        }
    },
}
