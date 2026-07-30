import type {ComputedRef, InjectionKey, Ref} from "vue"
import {NoCodeElement, TopologyClickParams} from "./utils/types"
import {Panel} from "../../utils/multiPanelTypes"
import type {FieldNavigation} from "./utils/useFieldNavigation"

export const BLOCK_SCHEMA_PATH_INJECTION_KEY = Symbol("block-schema-path-injection-key") as InjectionKey<ComputedRef<string>>
export const FULL_SOURCE_INJECTION_KEY = Symbol("flow-injection-key") as InjectionKey<ComputedRef<string>>
export const PLUGIN_DEFAULTS_INJECTION_KEY = Symbol("plugin-defaults-injection-key") as InjectionKey<ComputedRef<Record<string, unknown>>>
export const PARENT_PATH_INJECTION_KEY = Symbol("parent-path-injection-key") as InjectionKey<string>
export const REF_PATH_INJECTION_KEY = Symbol("ref-path-injection-key") as InjectionKey<number | undefined>
export const POSITION_INJECTION_KEY = Symbol("position-injection-key") as InjectionKey<"after" | "before">
export const CREATING_TASK_INJECTION_KEY = Symbol("creating-injection-key") as InjectionKey<boolean>
export const CREATING_FLOW_INJECTION_KEY = Symbol("creating-flow-injection-key") as InjectionKey<boolean>
export const FIELDNAME_INJECTION_KEY = Symbol("fieldname-injection-key") as InjectionKey<string | undefined>
export const EDITING_TASK_INJECTION_KEY = Symbol("editing-injection-key") as InjectionKey<boolean>
export const CREATE_TASK_FUNCTION_INJECTION_KEY = Symbol("creating-function-injection-key") as InjectionKey<(parentPath: string, blockSchemaPath: string, refPath: number | undefined, anchorEl?: HTMLElement) => void>
export const EDIT_TASK_FUNCTION_INJECTION_KEY = Symbol("edit-function-injection-key") as InjectionKey<(parentPath: string, blockSchemaPath: string, refPath: number | undefined, split?: boolean) => void>
export const CLOSE_TASK_FUNCTION_INJECTION_KEY = Symbol("close-function-injection-key") as InjectionKey<() => void>
export const UPDATE_YAML_FUNCTION_INJECTION_KEY = Symbol("update-function-injection-key") as InjectionKey<(yaml: string) => void>
export const PANEL_INJECTION_KEY = Symbol("panel-injection-key") as InjectionKey<Ref<any>>

export const TOPOLOGY_CLICK_INJECTION_KEY = Symbol("topology-click-injection-key") as InjectionKey<Ref<TopologyClickParams | undefined>>
export const VISIBLE_PANELS_INJECTION_KEY = Symbol("visible-panels-injection-key") as InjectionKey<Ref<Panel[]>>
export const PANEL_MAXIMIZED_INJECTION_KEY = Symbol("panel-maximized-injection-key") as InjectionKey<ComputedRef<boolean>>
export const EDITOR_CURSOR_INJECTION_KEY = Symbol("editor-cursor-injection-key") as InjectionKey<Ref<number | undefined>>
export const EDITOR_HIGHLIGHT_INJECTION_KEY = Symbol("editor-highlight-injection-key") as InjectionKey<Ref<number | undefined>>
export const EDITOR_WRAPPER_INJECTION_KEY = Symbol("editor-wrapper-injection-key") as InjectionKey<boolean>

export const ROOT_SCHEMA_INJECTION_KEY = Symbol("root-schema-injection-key") as InjectionKey<Ref<Record<string, any>>>

export const FULL_SCHEMA_INJECTION_KEY = Symbol("full-schema-injection-key") as InjectionKey<Ref<{
            definitions: Record<string, any>,
            $ref: string,
        }>>

export const SCHEMA_DEFINITIONS_INJECTION_KEY = Symbol("schema-definitions-injection-key") as InjectionKey<ComputedRef<Record<string, any>>>

export const DATA_TYPES_MAP_INJECTION_KEY = Symbol("data-types-injection-key") as InjectionKey<ComputedRef<Record<string, string[] | undefined>>>

export const ON_TASK_EDITOR_CLICK_INJECTION_KEY = Symbol("on-task-editor-click-injection-key") as InjectionKey<(elt?: Partial<NoCodeElement>) => void>

export const DEFAULT_NAMESPACE_INJECTION_KEY = Symbol("default-namespace-injection-key") as InjectionKey<ComputedRef<string>>

export const TENANTS_INJECTION_KEY = Symbol("tenants-injection-key") as InjectionKey<ComputedRef<{id: string; name?: string}[]>>

export const FIELD_NAV_INJECTION_KEY = Symbol("field-nav-injection-key") as InjectionKey<FieldNavigation>

export const BLOCK_VALIDATION_ISSUES_INJECTION_KEY = Symbol("block-validation-issues-injection-key") as InjectionKey<ComputedRef<Map<string, string[]>>>
