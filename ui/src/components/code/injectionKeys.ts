import type {ComputedRef, InjectionKey, Ref} from "vue"
import {Breadcrumb, BlockType, TopologyClickParams} from "./utils/types"
import {Panel} from "../MultiPanelTabs.vue"

/**
 * Complete flow YAML string for the no-code
 */
export const FLOW_INJECTION_KEY = Symbol("flow-injection-key") as InjectionKey<ComputedRef<string>>
/**
 * The type of the block that is being created
 */
export const BLOCKTYPE_INJECT_KEY = Symbol("blocktype-injection-key") as InjectionKey<BlockType | "pluginDefaults" | undefined>
/**
 * When creating a subtask, this is the parent task path
 */
export const PARENT_PATH_INJECTION_KEY = Symbol("parent-path-injection-key") as InjectionKey<string>
/**
 * Current task ID (When a task is edited) or target task ID (When a task is created) or task type (when a pluginDefaults is edited)
 */
export const REF_PATH_INJECTION_KEY = Symbol("ref-path-injection-key") as InjectionKey<number | undefined>
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
 * Call this when starting to create a new task, when the user clicks on the add button
 * to start the addition process
 */
export const CREATE_TASK_FUNCTION_INJECTION_KEY = Symbol("creating-function-injection-key") as InjectionKey<(blockType: BlockType | "pluginDefaults", parentPath: string, refPath: number | undefined) => void>
/**
 * Call this when starting to edit a task, when the user clicks on the task line
 * to start the edition process
 */
export const EDIT_TASK_FUNCTION_INJECTION_KEY = Symbol("edit-function-injection-key") as InjectionKey<(blockType: BlockType | "pluginDefaults", parentPath: string, refPath: number) => void>
/**
 * Call this when closing a task, when the user clicks on the close button
 */
export const CLOSE_TASK_FUNCTION_INJECTION_KEY = Symbol("close-function-injection-key") as InjectionKey<() => void>
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
 * When users click on one of topology buttons, such as create or edit, multi-panel view needs to react accordingly
 */
export const TOPOLOGY_CLICK_INJECTION_KEY = Symbol("topology-click-injection-key") as InjectionKey<Ref<TopologyClickParams | undefined>>
/**
* Array of visible panels in the multi-panel view
*/
export const VISIBLE_PANELS_INJECTION_KEY = Symbol("visible-panels-injection-key") as InjectionKey<Ref<Panel[]>>
/**
* The position of the cursor in the code editor
*/
export const EDITOR_CURSOR_INJECTION_KEY = Symbol("editor-cursor-injection-key") as InjectionKey<Ref<number | undefined>>
/**
* The range inside the code editor that we want to highlight
*/
export const EDITOR_HIGHLIGHT_INJECTION_KEY = Symbol("editor-highlight-injection-key") as InjectionKey<Ref<number | undefined>>