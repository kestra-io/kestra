import type {ComposerTranslation} from "vue-i18n"
import {SECTIONS} from "@kestra-io/design-system"
import AlertOutline from "vue-material-design-icons/AlertOutline.vue"
import InformationOutline from "vue-material-design-icons/InformationOutline.vue"
import SendLock from "vue-material-design-icons/SendLock.vue"
import TextBoxSearch from "vue-material-design-icons/TextBoxSearch.vue"
import LocationExit from "vue-material-design-icons/LocationExit.vue"
import OpenInNew from "vue-material-design-icons/OpenInNew.vue"
import UnfoldMoreHorizontal from "vue-material-design-icons/UnfoldMoreHorizontal.vue"
import EyeOutline from "vue-material-design-icons/EyeOutline.vue"
import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
import Delete from "vue-material-design-icons/Delete.vue"
import PlayBoxMultiple from "vue-material-design-icons/PlayBoxMultiple.vue"
import type {NodeAction} from "../nodes/NodeMenu.vue"
import type {CustomActionConfig, ShowDetailsConfig} from "./constants"

export interface ActionableTask {
    id?: string;
    description?: string;
    runIf?: unknown;
    errors?: unknown[];
}

export interface ActionableTaskRun {
    id: string;
    attempts?: unknown[];
}

export interface NodeActionsContext {
    task?: ActionableTask;
    taskId: string;
    isReadOnly: boolean;
    isFlowable: boolean;
    expandable: boolean;
    taskExecution?: unknown;
    taskRuns: ActionableTaskRun[];
    taskRunsWithDynamicChildren: unknown[];
    replayEnabled: boolean;
    link?: {namespace?: string; id?: string; executionId?: string};
    actionConfig?: {
        config: CustomActionConfig | ShowDetailsConfig;
        eventName: "showCustomAction" | "showDetails";
    };
}

export interface NodeActionCallbacks {
    onShowDescription: (payload: {id: string; description: string}) => void;
    onShowCondition: (payload: {id: string; task: ActionableTask; section: string}) => void;
    onShowLogs: (payload: {id: string; execution: unknown; taskRuns: unknown[]}) => void;
    onShowOutputs: (payload: {id: string; execution: unknown; taskRuns: unknown[]}) => void;
    onOpenLink: (payload: {link: {namespace?: string; id?: string; executionId?: string}}) => void;
    onExpand: (payload: {id: string; type: string}) => void;
    onAddError: (payload: {task: ActionableTask}) => void;
    onShowCustomAction: (payload: {task: ActionableTask; customAction: CustomActionConfig}) => void;
    onShowDetails: (payload: {task: ActionableTask; showDetails: ShowDetailsConfig}) => void;
    onDuplicate: (payload: {id: string}) => void;
    onDelete: (payload: {id: string; section: string}) => void;
    onReplayTask: (payload: {id: string; execution: unknown; taskRuns: unknown[]}) => void;
}

/** Shared between `TaskNode` (a plain task) and `ClusterNode` (a flowable's lane header) so the
 *  two never drift on what a task's own menu offers — see kestra-io/kestra#19666. */
export function buildNodeActions(
    ctx: NodeActionsContext,
    t: ComposerTranslation,
    callbacks: NodeActionCallbacks,
    expandData: {id: string; type: string},
): NodeAction[] {
    const list: NodeAction[] = []
    const task = ctx.task

    if (task?.description) {
        list.push({
            key: "description",
            label: t("show description"),
            icon: InformationOutline,
            onClick: () => callbacks.onShowDescription({id: ctx.taskId, description: task.description as string}),
        })
    }
    if (task?.runIf) {
        list.push({
            key: "condition",
            label: t("show task condition"),
            icon: SendLock,
            onClick: () => callbacks.onShowCondition({id: ctx.taskId, task: task as ActionableTask, section: SECTIONS.TASKS}),
        })
    }
    const hasExecution = Boolean(ctx.taskExecution)
    if (hasExecution) {
        list.push({
            key: "logs",
            label: t("show task logs"),
            icon: TextBoxSearch,
            onClick: () => callbacks.onShowLogs({id: ctx.taskId, execution: ctx.taskExecution, taskRuns: ctx.taskRunsWithDynamicChildren}),
        })
        list.push({
            key: "outputs",
            label: t("show task outputs"),
            icon: LocationExit,
            onClick: () => callbacks.onShowOutputs({id: ctx.taskId, execution: ctx.taskExecution, taskRuns: ctx.taskRuns}),
        })
    }
    if (ctx.link) {
        list.push({
            key: "open",
            label: t("open"),
            icon: OpenInNew,
            onClick: () => callbacks.onOpenLink({link: ctx.link as NonNullable<typeof ctx.link>}),
        })
    }
    if (ctx.expandable) {
        list.push({
            key: "expand",
            label: t("expand"),
            icon: UnfoldMoreHorizontal,
            onClick: () => callbacks.onExpand(expandData),
        })
    }
    if (!hasExecution && !ctx.isReadOnly && ctx.isFlowable && !task?.errors?.length) {
        list.push({
            key: "add-error",
            label: t("add error handler"),
            icon: AlertOutline,
            onClick: () => callbacks.onAddError({task: task as ActionableTask}),
        })
    }
    if (ctx.actionConfig && task) {
        list.push({
            key: "show-details",
            label: ctx.actionConfig.config.label || t("show details"),
            icon: EyeOutline,
            onClick: () => {
                if (ctx.actionConfig?.eventName === "showCustomAction") {
                    callbacks.onShowCustomAction({task, customAction: ctx.actionConfig.config as CustomActionConfig})
                } else {
                    callbacks.onShowDetails({task, showDetails: ctx.actionConfig!.config as ShowDetailsConfig})
                }
            },
        })
    }
    if (!ctx.isReadOnly) {
        list.push({
            key: "duplicate",
            label: t("block_editor.duplicate"),
            icon: ContentCopy,
            divided: true,
            onClick: () => callbacks.onDuplicate({id: ctx.taskId}),
        })
        list.push({
            key: "delete",
            label: t("delete"),
            icon: Delete,
            danger: true,
            divided: true,
            onClick: () => callbacks.onDelete({id: ctx.taskId, section: SECTIONS.TASKS}),
        })
    }
    if (ctx.replayEnabled && hasExecution && ctx.taskRuns.length > 0) {
        list.push({
            key: "replay",
            label: t("replay"),
            icon: PlayBoxMultiple,
            divided: true,
            onClick: () => callbacks.onReplayTask({id: ctx.taskId, execution: ctx.taskExecution, taskRuns: ctx.taskRuns}),
        })
    }

    return list
}
