import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {defineComponent, ref, shallowRef, toRef, type PropType} from "vue"
import type * as monaco from "monaco-editor/editor/editor.api"
import {KsEditor} from "@kestra-io/design-system"

import {useViolationMarkers} from "../../../../src/composables/useViolationMarkers"
import type {ValidationError} from "../../../../src/utils/validationErrors"

const MarkedEditor = defineComponent({
    props: {
        source: {type: String, required: true},
        errors: {type: Array as PropType<ValidationError[]>, required: true},
    },
    setup(props) {
        const code = ref(props.source)
        const editor = shallowRef<monaco.editor.IStandaloneCodeEditor>()
        useViolationMarkers({editor, errors: toRef(props, "errors")})

        return () => (
            <div style="height: 520px">
                <KsEditor
                    v-model={code.value}
                    lang="yaml"
                    onEditorMounted={(mounted?: monaco.editor.IStandaloneCodeEditor | monaco.editor.IStandaloneDiffEditor) => {
                        editor.value = mounted as monaco.editor.IStandaloneCodeEditor
                    }}
                />
            </div>
        )
    },
})

const meta: Meta<typeof MarkedEditor> = {
    title: "flows/ViolationMarkers",
    component: MarkedEditor,
}

export default meta

type Story = StoryObj<typeof MarkedEditor>

const squiggles = `id: squiggles
namespace: company.team

tasks:
  - id: hello
    type: io.kestra.plugin.core.log.Log
    message: Hi
    colour: purple                            # unknown key

  - id: nested
    type: io.kestra.plugin.core.flow.Sequential
    tasks:
      - id: silent
        type: io.kestra.plugin.core.log.Log   # missing message

  - id: ghost
    type: io.kestra.plugin.core.log.Nope      # unknown type

triggers:
  - id: daily
    type: io.kestra.plugin.core.trigger.Schedule   # missing cron
`

const unknownKey = {pointer: "/tasks/0/colour", detail: "Unrecognized field \"colour\" (class io.kestra.plugin.core.log.Log), not marked as ignorable"}
const missingMessage = {pointer: "/tasks/1/tasks/0/message", detail: "must not be null"}
const unknownType = {pointer: "/tasks/2/type", detail: "Invalid type: io.kestra.plugin.core.log.Nope"}
const missingCron = {pointer: "/triggers/0/cron", detail: "must not be null"}

export const UnknownKey: Story = {args: {source: squiggles, errors: [unknownKey]}}

export const MissingKey: Story = {args: {source: squiggles, errors: [missingMessage]}}

export const UnknownType: Story = {args: {source: squiggles, errors: [unknownType]}}

export const RootViolationStaysInThePanel: Story = {
    args: {source: squiggles, errors: [{pointer: "/labels", detail: "must not be empty"}]},
}

export const AllTogether: Story = {
    args: {source: squiggles, errors: [unknownKey, missingMessage, unknownType, missingCron]},
}

const script = `id: script
namespace: company.team

tasks:
  - id: transform
    type: io.kestra.plugin.scripts.python.Script
    script: |
      import pandas as pd
      df = pd.read_csv("in.csv")
      print("done")
    beforeCommands:
      - pip install pandas
`

export const BlockScalarBodyStaysClean: Story = {
    args: {source: script, errors: [{pointer: "/tasks/0/containerImage", detail: "must not be null"}]},
}
