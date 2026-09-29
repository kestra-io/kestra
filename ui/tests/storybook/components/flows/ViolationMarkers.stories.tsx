import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {defineComponent, ref, shallowRef, type PropType} from "vue"
import type * as monaco from "monaco-editor/editor/editor.api"
import {KsEditor} from "@kestra-io/design-system"

import {useViolationMarkers} from "../../../../src/composables/useViolationMarkers"
import type {LocatedViolation} from "../../../../src/utils/violationMarkers"

const MarkedEditor = defineComponent({
    props: {
        source: {type: String, required: true},
        violations: {type: Array as PropType<LocatedViolation[]>, required: true},
    },
    setup(props) {
        const code = ref(props.source)
        const editor = shallowRef<monaco.editor.IStandaloneCodeEditor>()
        useViolationMarkers({editor, violations: ref(props.violations)})

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

const unknownKey = {path: "/tasks/0/colour", message: "Unrecognized field \"colour\" (class io.kestra.plugin.core.log.Log), not marked as ignorable"}
const missingMessage = {path: "/tasks/1/tasks/0/message", message: "must not be null"}
const unknownType = {path: "/tasks/2/type", message: "Invalid type: io.kestra.plugin.core.log.Nope"}
const missingCron = {path: "/triggers/0/cron", message: "must not be null"}

export const UnknownKey: Story = {args: {source: squiggles, violations: [unknownKey]}}

export const MissingKey: Story = {args: {source: squiggles, violations: [missingMessage]}}

export const UnknownType: Story = {args: {source: squiggles, violations: [unknownType]}}

export const RootViolationStaysInThePanel: Story = {
    args: {source: squiggles, violations: [{path: "/labels", message: "must not be empty"}]},
}

export const AllTogether: Story = {
    args: {source: squiggles, violations: [unknownKey, missingMessage, unknownType, missingCron]},
}
