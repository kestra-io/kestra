import {type Component} from "vue"
import TriggerIcon from "vue-material-design-icons/LightningBoltOutline.vue"
import TasksIcon from "vue-material-design-icons/FormatListBulleted.vue"
import ErrorIcon from "vue-material-design-icons/AlertCircleOutline.vue"
import FinallyIcon from "vue-material-design-icons/FlagOutline.vue"
import AfterExecutionIcon from "vue-material-design-icons/FlagCheckered.vue"
import type {BlockSection} from "../../../utils/flowableBlockOps"

type Translate = (key: string, named?: Record<string, unknown>) => string

export interface SectionLaneConfig {
    section: BlockSection
    title: string
    icon: Component
    addLabel: string
    addTest?: string
    tone?: "default" | "error" | "warning"
    blocks: Record<string, unknown>[]
    emptyLabel: string
    emptyHint?: string
    listTest?: string
    endDropTest?: string
    supportsFlowable?: boolean
    clusterAcceptsDrop?: boolean
    playgroundEnabled: boolean
}

export interface SectionLaneSources {
    triggers: Record<string, unknown>[]
    tasks: Record<string, unknown>[]
    errors: Record<string, unknown>[]
    finally: Record<string, unknown>[]
    afterExecution: Record<string, unknown>[]
}

export function buildSectionLanes(
    t: Translate,
    blocks: SectionLaneSources,
    playgroundEnabled: boolean,
): SectionLaneConfig[] {
    const taskNoun = t("block_editor.task_noun")
    const addTask = t("block_editor.add_task")

    return [
        {
            section: "triggers",
            title: t("no_code.sections.triggers"),
            icon: TriggerIcon,
            addLabel: t("block_editor.add_trigger"),
            blocks: blocks.triggers,
            emptyLabel: t("block_editor.trigger_noun"),
            listTest: "block-editor-trigger-list",
            playgroundEnabled: false,
        },
        {
            section: "tasks",
            title: t("no_code.sections.tasks"),
            icon: TasksIcon,
            addLabel: addTask,
            addTest: "block-editor-add-task",
            blocks: blocks.tasks,
            emptyLabel: taskNoun,
            emptyHint: t("block_editor.empty_add_hint"),
            listTest: "block-editor-task-list",
            endDropTest: "block-editor-tasks-end",
            supportsFlowable: true,
            clusterAcceptsDrop: true,
            playgroundEnabled,
        },
        {
            section: "errors",
            title: t("block_editor.lane_errors"),
            icon: ErrorIcon,
            addLabel: addTask,
            blocks: blocks.errors,
            emptyLabel: taskNoun,
            tone: "error",
            supportsFlowable: true,
            playgroundEnabled,
        },
        {
            section: "finally",
            title: t("block_editor.lane_finally"),
            icon: FinallyIcon,
            addLabel: addTask,
            blocks: blocks.finally,
            emptyLabel: taskNoun,
            tone: "warning",
            supportsFlowable: true,
            playgroundEnabled,
        },
        {
            section: "afterExecution",
            title: t("no_code.sections.afterExecution"),
            icon: AfterExecutionIcon,
            addLabel: addTask,
            blocks: blocks.afterExecution,
            emptyLabel: taskNoun,
            supportsFlowable: true,
            playgroundEnabled,
        },
    ]
}
