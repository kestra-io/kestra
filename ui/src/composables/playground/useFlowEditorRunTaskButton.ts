import {computed, Ref, watch} from "vue"
import {useI18n} from "vue-i18n"
import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
import type {EditorGutterActionEvent, KsEditor} from "@kestra-io/design-system"

import {usePlaygroundStore} from "../../stores/playground"
import {useFlowStore} from "../../stores/flow"

/** Puts a run glyph in the gutter beside each task of the flow; a click runs it, Shift+click also runs what follows. */
export default function useFlowEditorRunTaskButton(isCurrentTabFlow: Ref<boolean>, editorRefElement: Ref<InstanceType<typeof KsEditor> | undefined>, source: Ref<string>) {
    const {t} = useI18n()
    const playgroundStore = usePlaygroundStore()
    const flowStore = useFlowStore()

    const gutterActions = computed(() => {
        if (!isCurrentTabFlow.value || !playgroundStore.available || flowStore.isReadOnly) return []
        return Object.entries(YAML_UTILS.getTasksLines(source.value))
            .map(([taskId, {start}]) => ({id: taskId, line: start, title: t("playground.run_task_gutter", {id: taskId})}))
    })

    watch([gutterActions, editorRefElement], ([actions, editor]) => editor?.setGutterActions(actions), {immediate: true})

    function runGutterAction({id, shiftKey}: EditorGutterActionEvent) {
        playgroundStore.runUntilTask(id, shiftKey)
    }

    return {runGutterAction}
}
