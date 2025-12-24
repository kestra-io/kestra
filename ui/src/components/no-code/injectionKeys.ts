import type {ComputedRef, InjectionKey, Ref} from "vue"
import {NoCodeElement, TopologyClickParams} from "./utils/types"
import {Panel} from "../../utils/multiPanelTypes"

export const BLOCK_SCHEMA_PATH_INJECTION_KEY = Symbol("block-schema-path-injection-key") as InjectionKey<ComputedRef<string>>
/**
 * Complete flow YAML string for the no-code
 */
export const FULL_SOURCE_INJECTION_KEY = Symbol("flow-injection-key") as InjectionKey<ComputedRef<string>>
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
export const CREATING_TASK_INJECTION_KEY = Symbol("creating-injection-key") as InjectionKey<boolean>
export const CREATING_FLOW_INJECTION_KEY = Symbol("creating-flow-injection-key") as InjectionKey<boolean>
/**
 * When creating anew task, allows to specify a field where the new task should be injected.
 * @example
 * ```yaml
 * tasks:
 *   - task: # this is the fieldName
 *       id: myTask
 *       type: io.kestra.core.tasks.shell.Bash
 * ```
 */
export const FIELDNAME_INJECTION_KEY = Symbol("fieldname-injection-key") as InjectionKey<string | undefined>
export const EDITING_TASK_INJECTION_KEY = Symbol("editing-injection-key") as InjectionKey<boolean>
/**
 * Call this when starting to create a new task, when the user clicks on the add button
 * to start the addition process
 */
export const CREATE_TASK_FUNCTION_INJECTION_KEY = Symbol("creating-function-injection-key") as InjectionKey<(parentPath: string, blockSchemaPath: string, refPath: number | undefined) => void>
/**
 * Call this when starting to edit a task, when the user clicks on the task line
 * to start the edition process
 */
export const EDIT_TASK_FUNCTION_INJECTION_KEY = Symbol("edit-function-injection-key") as InjectionKey<(parentPath: string, blockSchemaPath: string, refPath: number | undefined) => void>
/**
 * Call this when closing a task, when the user clicks on the close button
 */
export const CLOSE_TASK_FUNCTION_INJECTION_KEY = Symbol("close-function-injection-key") as InjectionKey<() => void>
/**
 * Call this function to update the full Yaml content
 */
export const UPDATE_YAML_FUNCTION_INJECTION_KEY = Symbol("update-function-injection-key") as InjectionKey<(yaml: string) => void>
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
/**
* Indicates if the Monaco editor is being used within EditorWrapper context for flow editing
*/
export const EDITOR_WRAPPER_INJECTION_KEY = Symbol("editor-wrapper-injection-key") as InjectionKey<boolean>

export const ROOT_SCHEMA_INJECTION_KEY = Symbol("root-schema-injection-key") as InjectionKey<Ref<Record<string, any>>>

export const FULL_SCHEMA_INJECTION_KEY = Symbol("full-schema-injection-key") as InjectionKey<Ref<{
            definitions: Record<string, any>,
            $ref: string,
        }>>

export const SCHEMA_DEFINITIONS_INJECTION_KEY = Symbol("schema-definitions-injection-key") as InjectionKey<ComputedRef<Record<string, any>>>

export const DATA_TYPES_MAP_INJECTION_KEY = Symbol("data-types-injection-key") as InjectionKey<ComputedRef<Record<string, string[] | undefined>>>

export const ON_TASK_EDITOR_CLICK_INJECTION_KEY = Symbol("on-task-editor-click-injection-key") as InjectionKey<(elt?: Partial<NoCodeElement>) => void>;

export const DEFAULT_NAMESPACE_INJECTION_KEY = Symbol("default-namespace-injection-key") as InjectionKey<ComputedRef<string>>;