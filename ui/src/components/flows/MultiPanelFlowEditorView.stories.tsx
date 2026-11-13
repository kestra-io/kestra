import type {Meta, StoryFn} from "@storybook/vue3-vite";
import {vueRouter} from "storybook-vue3-router";
import MultiPanelFlowEditorView from "./MultiPanelFlowEditorView.vue";
import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils";
import type {FlowGraph} from "@kestra-io/topology/vue-flow-utils";
import allowFailureDemo from "../../../tests/fixtures/flowgraphs/allow-failure-demo.json";
import loopUntilDemo from "../../../tests/fixtures/flowgraphs/loop-until-demo.json";
import flowSchema from "../../stores/flow-schema.json";
import {setMockClient, type AxiosLikeClient} from "@kestra-io/kestra-sdk"
import {mockClientFallback, mockResponse} from "../../../.storybook/apiMock";
import {useFlowStore, type Flow} from "../../stores/flow";
import {expect, waitFor} from "storybook/test";
import * as monaco from "monaco-editor/editor/editor.api";
import {READ_ONLY_LINE_CLASS} from "../../composables/useReadOnlyYamlKeys";


export default {
    title: "Components/MultiPanelFlowEditorView",
    component: MultiPanelFlowEditorView,
    decorators: [
        vueRouter([
            {
                path: "/",
                name: "home",
                component: {template: "<div>home</div>"}
            },
            {
                path: "/flows/edit/:namespace/",
                name: "flows/edit",
                component: {template: "<div>update flows</div>"}
            }
        ])
    ]
} as Meta<typeof MultiPanelFlowEditorView>;

const Template: StoryFn<{flow: string; flowGraph?: FlowGraph}> = (args) => ({
    setup() {
        const axios: Partial<AxiosLikeClient> = {}
        const flowStore = useFlowStore()
        axios.get = async <T,>(uri: string) => {
            if (uri.endsWith("/plugins")) {
                return mockResponse<T>([])
            }
            if (uri.endsWith("/flow")) {
                return mockResponse<T>(flowSchema)
            }
            if (uri.endsWith("/distinct-namespaces")) {
                return mockResponse<T>(["sanitychecks.flows.blueprints", "tutorial"])
            }
            // Anything this story doesn't answer itself falls back to the shared table in
            // .storybook/apiMock.ts, which reports the route if nothing there covers it either.
            return mockClientFallback<T>("GET", uri)
        }
        axios.post = async <T,>(uri: string, data?: unknown) => {
            if (uri.endsWith("/graph")) {
                return mockResponse<T>(args.flowGraph ?? allowFailureDemo)
            }
            if (uri.endsWith("/validate")) {
                return mockResponse<T>({})
            }
            return mockClientFallback<T>("POST", uri, data)
        }
        setMockClient(axios);

        const flow = YAML_UTILS.parse<Flow>(args.flow)
        if (flow) {
            flow.source = args.flow
            flowStore.flow = flow
        }
        flowStore.flowGraph = (args.flowGraph ?? allowFailureDemo) as FlowGraph
        flowStore.flowYaml = args.flow

        return () =>
            <div style="height: 100vh">
                <MultiPanelFlowEditorView/>
            </div>
    }
});

export const Default = Template.bind({});
Default.args = {
    flow: `
id: allow-failure-demo
namespace: sanitychecks.flows.blueprints
tasks:
  - id: allow_failure
    type: io.kestra.plugin.core.flow.AllowFailure
    tasks:
      - id: fail_silently
        type: io.kestra.plugin.scripts.shell.Commands
        taskRunner:
          type: io.kestra.plugin.core.runner.Process
        commands:
          - exit 1
  - id: print_to_console
    type: io.kestra.plugin.scripts.shell.Commands
    taskRunner:
      type: io.kestra.plugin.core.runner.Process
    commands:
      - echo "this will run since previous failure was allowed ✅"
`.trim(),
};

export const EmptyFlow = Template.bind({});
EmptyFlow.args = {
    flow: `
id: empty
namespace: sanitychecks.flows.blueprints
`.trim(),
};

export const ComplexFlow = Template.bind({});
ComplexFlow.args = {
    flow: `
id: hello-world
namespace: tutorial
description: Hello World

inputs:
  - id: user
    type: STRING
    defaults: Rick Astley

tasks:
  - id: first_task
    type: io.kestra.plugin.core.debug.Return
    format: thrilled

  - id: second_task
    type: io.kestra.plugin.scripts.shell.Commands
    commands:
      - sleep 0.42
      - echo '::{"outputs":{"returned_data":"mydata"}}::'

  - id: hello_world
    type: io.kestra.plugin.core.log.Log
    message: |
      Welcome to Kestra, {{ inputs.user }}!
      We are {{ outputs.first_task.value}} to have You here!

triggers:
  - id: daily
    type: io.kestra.plugin.core.trigger.Schedule
    disabled: true
    cron: 0 9 * * *
`.trim(),
};

export const LoopUntil = Template.bind({});
LoopUntil.args = {
    flow: `
id: loop-until-demo
namespace: tutorial
tasks:
  - id: wait_for_link
    type: io.kestra.plugin.core.flow.LoopUntil
    checkFrequency:
      interval: PT30S
      maxDuration: PT5M
      maxIterations: 5
    condition: "{{ fromJson(outputs.generate_download_link.body).status == 'ok' }}"
    tasks:
      - id: generate_download_link
        type: io.kestra.plugin.core.log.Log
        message: "Hello"
`.trim(),
    flowGraph: loopUntilDemo,
};

const LOCKED_FLOW = `
id: locked-demo
namespace: tutorial
tasks:
  - id: hello
    type: io.kestra.plugin.core.log.Log
    message: Hello
`.trim();

const MESSAGE_LINE = 6

async function lockedEditor() {
    return waitFor(() => {
        const editor = monaco.editor.getEditors().find((candidate) => candidate.getValue() === LOCKED_FLOW)
        expect(editor).toBeTruthy()
        expect(document.querySelector(`.${READ_ONLY_LINE_CLASS}`)).toBeTruthy()
        return editor as monaco.editor.IStandaloneCodeEditor
    }, {timeout: 10000})
}

async function settle() {
    await new Promise((resolve) => setTimeout(resolve, 1200))
}

function expectNoToast() {
    expect(document.querySelector("[class*='notification']")).toBeNull()
}

function lockedStory(play: (editor: monaco.editor.IStandaloneCodeEditor) => Promise<void>) {
    const story = Template.bind({}) as StoryFn<{flow: string; flowGraph?: FlowGraph}> & {play?: () => Promise<void>}
    story.args = {flow: LOCKED_FLOW}
    story.play = async () => {
        const editor = await lockedEditor()
        editor.focus()
        await play(editor)
    }
    return story
}

export const LockedTypingBeforeKey = lockedStory(async (editor) => {
    editor.setPosition({lineNumber: 1, column: 1})

    editor.trigger("keyboard", "type", {text: "x"})
    await settle()

    expect(editor.getPosition()).toMatchObject({lineNumber: 1, column: 1})
    expect(editor.getValue()).toBe(LOCKED_FLOW)
    expectNoToast()
})

export const LockedLineJoin = lockedStory(async (editor) => {
    editor.setPosition({lineNumber: 2, column: 1})

    editor.trigger("keyboard", "deleteLeft", {})
    await settle()

    expect(editor.getPosition()).toMatchObject({lineNumber: 2, column: 1})
    expect(editor.getValue()).toBe(LOCKED_FLOW)
    expectNoToast()
})

export const LockedLineDelete = lockedStory(async (editor) => {
    const selection = {startLineNumber: 1, startColumn: 1, endLineNumber: 2, endColumn: 1}
    editor.setSelection(selection)

    editor.trigger("keyboard", "deleteLeft", {})
    await settle()

    expect(editor.getSelection()).toMatchObject(selection)
    expect(editor.getValue()).toBe(LOCKED_FLOW)
    expectNoToast()
})

export const LockedPasteOverDocument = lockedStory(async (editor) => {
    const selection = editor.getModel()!.getFullModelRange()
    editor.setSelection(selection)

    editor.executeEdits("paste", [{range: selection, text: "tasks: []"}])
    await settle()

    expect(editor.getSelection()).toMatchObject(selection)
    expect(editor.getValue()).toBe(LOCKED_FLOW)
    expectNoToast()
})

export const LockedMultiCursor = lockedStory(async (editor) => {
    const selections = [
        {selectionStartLineNumber: 1, selectionStartColumn: 1, positionLineNumber: 1, positionColumn: 1},
        {selectionStartLineNumber: MESSAGE_LINE, selectionStartColumn: 1, positionLineNumber: MESSAGE_LINE, positionColumn: 1},
    ]
    editor.setSelections(selections)

    editor.trigger("keyboard", "type", {text: "x"})
    await settle()

    expect(editor.getSelections()).toHaveLength(2)
    expect(editor.getSelections()?.[0]).toMatchObject({positionLineNumber: 1, positionColumn: 1})
    expect(editor.getValue()).toBe(LOCKED_FLOW)
    expectNoToast()
})

export const LockedUndoAfterRefusedEdit = lockedStory(async (editor) => {
    editor.setPosition({lineNumber: 1, column: 1})
    editor.trigger("keyboard", "type", {text: "x"})
    await settle()

    editor.trigger("keyboard", "undo", {})
    await settle()

    expect(editor.getValue()).toBe(LOCKED_FLOW)
    expectNoToast()
})

export const LockedWithCrlf = lockedStory(async (editor) => {
    editor.getModel()!.setEOL(monaco.editor.EndOfLineSequence.CRLF)
    const crlfValue = editor.getValue()
    editor.setPosition({lineNumber: 2, column: 1})

    editor.trigger("keyboard", "deleteLeft", {})
    await settle()

    expect(editor.getValue()).toBe(crlfValue)
    expect(editor.getPosition()).toMatchObject({lineNumber: 2, column: 1})
    expectNoToast()
})

export const EditingElsewhereStaysFree = lockedStory(async (editor) => {
    const endOfMessage = editor.getModel()!.getLineMaxColumn(MESSAGE_LINE)
    editor.setPosition({lineNumber: MESSAGE_LINE, column: endOfMessage})

    editor.trigger("keyboard", "type", {text: "!"})
    await settle()

    expect(editor.getValue()).toBe(LOCKED_FLOW.replace("message: Hello", "message: Hello!"))
    expect(editor.getPosition()).toMatchObject({lineNumber: MESSAGE_LINE, column: endOfMessage + 1})
    expectNoToast()
})

export const EnterAtEndOfLockedLineIsAllowed = lockedStory(async (editor) => {
    editor.setPosition({lineNumber: 1, column: editor.getModel()!.getLineMaxColumn(1)})

    editor.trigger("keyboard", "type", {text: "\n"})
    await settle()

    expect(editor.getValue()).toBe(LOCKED_FLOW.replace("id: locked-demo\n", "id: locked-demo\n\n"))
    expect(editor.getPosition()).toMatchObject({lineNumber: 2})
    expectNoToast()
})
