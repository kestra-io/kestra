import type {ComputedRef, InjectionKey, Ref} from "vue"
import {Breadcrumb, SectionKey} from "./utils/types"

/**
 * Complete flow YAML string for the no-code
 */
export const FLOW_INJECTION_KEY = Symbol("flow-injection-key") as InjectionKey<ComputedRef<string>>
/**
 * Current section name (Where a task is created or edited)
 */
export const SECTION_INJECTION_KEY = Symbol("section-injection-key") as InjectionKey<Ref<SectionKey>>
/**
 * Current task ID (When a task is edited) or target task ID (When a task is created) or task type (when a pluginDefaults is edited)
 */
export const TASKID_INJECTION_KEY = Symbol("taskid-injection-key") as InjectionKey<Ref<string>>
/**
 * Tells if the task should eb added before or after the target (When a task is created)
 */
export const POSITION_INJECTION_KEY = Symbol("position-injection-key") as InjectionKey<"after" | "before">
/**
 * Tells if the task is being created or edited. Used to discriminate when a section is specified
 * NOTE: different from the `isCreating` flag coming from the store. `isCreating` refers to the Complete flow being in creation
 */
export const CREATING_TASK_INJECTION_KEY = Symbol("creating-injection-key") as InjectionKey<ComputedRef<boolean>>
/**
 * auto in multi-panel mode, button in legacy edit mode
 * Kept for backward compatibility
 * @deprecated
 */
export const SAVEMODE_INJECTION_KEY = Symbol("flow-id-injection-key") as InjectionKey<"button" | "auto">
/**
 * Call this when starting to create a new task, when the user clicks on the add button
 * to start the addition process
 */
export const CREATE_TASK_FUNCTION_INJECTION_KEY = Symbol("creating-function-injection-key") as InjectionKey<(section: string) => void>
/**
 * Call this when starting to edit a task, when the user clicks on the task line
 * to start the edition process
 */
export const EDIT_TASK_FUNCTION_INJECTION_KEY = Symbol("edit-function-injection-key") as InjectionKey<(section: string, taskId: string) => void>
/**
 * Call this when closing a task, when the user clicks on the close button
 */
export const CLOSE_TASK_FUNCTION_INJECTION_KEY = Symbol("close-function-injection-key") as InjectionKey<() => void>
/**
 * Index in the open creation tabs list
 * When users autosave on any of the tabs, to avoid losing the other tasks added at the same time, we assign them a number
 * and we use this number to order the tasks in the flow
 * NOTE: numbers are unique per section at a single moment. But once a number is freed, it can be reused.
 */
export const TASK_CREATION_INDEX_INJECTION_KEY = Symbol("task-creation-index-injection-key") as InjectionKey<ComputedRef<number>>
/**
 * Breadcrumbs for the no-code panel
 */
export const BREADCRUMB_INJECTION_KEY = Symbol("breadcrumb-injection-key") as InjectionKey<Ref<Breadcrumb[]>>
/**
 * Set this to override the contents of the no-code editor with a component of your choice
 * This is used to display the metadata edition inputs
 */
export const PANEL_INJECTION_KEY = Symbol("panel-injection-key") as InjectionKey<Ref<any>>
/**
 * When creating a subtask, this is the parent task ID
 * undefined when creating a task at the root level
 */
export const PARENT_TASKID_INJECTION_KEY = Symbol("parent-taskid-injection-key") as InjectionKey<Ref<string | undefined>>