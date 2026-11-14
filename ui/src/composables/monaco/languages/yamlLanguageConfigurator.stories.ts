import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, userEvent, waitFor} from "storybook/test"
import {KsEditor} from "@kestra-io/design-system"
import * as monaco from "monaco-editor/editor/editor.api"
import {StandaloneServices} from "monaco-editor/editor/standalone/browser/standaloneServices"
import {ILanguageFeaturesService} from "monaco-editor/editor/common/services/languageFeatures"
import flowSchemaUrl from "./yamlLanguageConfigurator.stories.flowSchema.json?url"
import {useEditorBindings} from "../../useEditorBindings"

// The yaml worker fetches its schema itself, out of reach of the API mock, and a worker started by an earlier story keeps
// the schema it failed to load, so each flow names its own schema in a modeline instead.
const SCHEMA_MODELINE = `# yaml-language-server: $schema=${new URL(flowSchemaUrl, location.href).href}\n`

let editor: monaco.editor.IStandaloneCodeEditor | undefined

const meta: Meta = {
    title: "Composables/Monaco/YamlLanguageConfigurator",
    beforeEach() {
        editor = undefined
    },
    render: (args) => ({
        components: {KsEditor},
        setup() {
            const bindings = useEditorBindings()
            const onMounted = (mounted: monaco.editor.IStandaloneCodeEditor) => {
                editor = mounted
            }
            return {args, bindings, onMounted}
        },
        template: `<div style="height: 400px">
            <KsEditor
                :modelValue="args.flow"
                lang="yaml"
                schemaType="flow"
                theme="light"
                :configureLanguage="bindings.configureLanguage"
                @editorMounted="onMounted"
            />
        </div>`,
    }),
}

export default meta
type Story = StoryObj<typeof meta>

const CURSOR = "|"

async function mountedEditor(): Promise<monaco.editor.IStandaloneCodeEditor> {
    await waitFor(() => expect(editor?.getModel()).toBeTruthy(), {timeout: 10000})
    return editor!
}

function cursorPosition(flow: string): monaco.IPosition {
    const before = flow.slice(0, flow.indexOf(CURSOR)).split("\n")
    return {lineNumber: before.length, column: before[before.length - 1].length + 1}
}

async function suggestionsAt(flow: string, kind: monaco.languages.CompletionItemKind): Promise<string[]> {
    const target = await mountedEditor()
    const model = target.getModel()!
    const provider = (StandaloneServices.get(ILanguageFeaturesService).completionProvider as unknown as {
        _entries: {selector: string; provider: monaco.languages.CompletionItemProvider}[];
    })._entries.filter((entry) => entry.selector === "yaml").at(-1)!.provider
    const {lineNumber, column} = cursorPosition(flow)
    const result = await provider.provideCompletionItems(
        model,
        new monaco.Position(lineNumber, column),
        {triggerKind: monaco.languages.CompletionTriggerKind.Invoke},
        new monaco.CancellationTokenSource().token,
    )
    return (result?.suggestions ?? [])
        .filter((suggestion) => suggestion.kind === kind)
        .map((suggestion) => String(suggestion.label))
        .sort()
}

async function expectSuggestions(flow: string, kind: monaco.languages.CompletionItemKind, labels: string[]) {
    await waitFor(async () => expect(await suggestionsAt(flow, kind)).toEqual(labels), {timeout: 10000})
}

const flowStory = (flow: string, play: (flow: string) => Promise<void>): Story => ({
    args: {flow: SCHEMA_MODELINE + flow.replace(CURSOR, "")},
    play: () => play(SCHEMA_MODELINE + flow),
})

const PROPERTY = monaco.languages.CompletionItemKind.Property

export const TypedInputSuggestsOnlyItsKeys = flowStory(
    "id: f\ninputs:\n  - id: asset\n    type: STRING\n    |\n",
    (flow) => expectSuggestions(flow, PROPERTY, ["defaults", "validator"]),
)

export const TypedTaskSuggestsOnlyItsKeys = flowStory(
    "id: f\ntasks:\n  - id: hello\n    type: io.kestra.plugin.core.log.Log\n    mes|\n",
    (flow) => expectSuggestions(flow, PROPERTY, ["message"]),
)

export const TaskNestedInFlowableSuggestsOnlyItsKeys = flowStory(
    "id: f\ntasks:\n  - id: parallel\n    type: io.kestra.plugin.core.flow.Parallel\n    tasks:\n      - id: hello\n        type: io.kestra.plugin.core.log.Log\n        |\n",
    (flow) => expectSuggestions(flow, PROPERTY, ["message"]),
)

export const TypeValueSuggestsEveryType = flowStory(
    "id: f\ninputs:\n  - id: asset\n    type: STRING|\n",
    (flow) => expectSuggestions(flow, monaco.languages.CompletionItemKind.Value, ["ARRAY", "INT", "STRING"]),
)

export const UntypedInputSuggestsSharedKeyOnce = flowStory(
    "id: f\ninputs:\n  - id: asset\n    |\n",
    async (flow) => {
        await waitFor(async () => {
            const labels = await suggestionsAt(flow, PROPERTY)
            expect(labels).toContain("validator")
            expect(labels.filter((label) => label === "defaults")).toHaveLength(1)
        }, {timeout: 10000})
    },
)

export const SharedKeyAcceptedAfterBareDashKeepsTheDashSpacing = flowStory(
    "id: f\ninputs:\n  - id: a\n    type: STRING\n  -|\n",
    async (flow) => {
        await waitFor(async () => expect(await suggestionsAt(flow, PROPERTY)).toContain("defaults"), {timeout: 10000})
        const target = await mountedEditor()
        target.focus()
        target.setPosition(cursorPosition(flow))
        target.trigger("story", "editor.action.triggerSuggest", {})
        const row = await waitFor(() => {
            const found = [...document.querySelectorAll<HTMLElement>(".suggest-widget .monaco-list-row")]
                .find((element) => element.getAttribute("aria-label")?.startsWith("defaults"))
            expect(found).toBeTruthy()
            return found!
        }, {timeout: 10000})
        await userEvent.click(row)
        await waitFor(() => expect(target.getModel()!.getLineContent(cursorPosition(flow).lineNumber)).toBe("  - defaults: "))
    },
)
