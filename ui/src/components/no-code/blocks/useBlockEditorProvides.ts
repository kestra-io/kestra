import {computed, provide, type ComputedRef, type Ref} from "vue"
import {useFlowStore} from "../../../stores/flow"
import {usePluginsStore} from "../../../stores/plugins"
import {defaultNamespace} from "../../../composables/useNamespaces"
import type {NoCodeProps} from "../../flows/noCodeTypes"
import {
    BLOCK_SCHEMA_PATH_INJECTION_KEY,
    BLOCK_VALIDATION_ISSUES_INJECTION_KEY,
    CLOSE_TASK_FUNCTION_INJECTION_KEY,
    CREATE_TASK_FUNCTION_INJECTION_KEY,
    CREATING_FLOW_INJECTION_KEY,
    CREATING_TASK_INJECTION_KEY,
    DEFAULT_NAMESPACE_INJECTION_KEY,
    EDIT_TASK_FUNCTION_INJECTION_KEY,
    EDITING_TASK_INJECTION_KEY,
    FIELDNAME_INJECTION_KEY,
    FULL_SCHEMA_INJECTION_KEY,
    FULL_SOURCE_INJECTION_KEY,
    PANEL_INJECTION_KEY,
    PARENT_PATH_INJECTION_KEY,
    POSITION_INJECTION_KEY,
    REF_PATH_INJECTION_KEY,
    ROOT_SCHEMA_INJECTION_KEY,
    SCHEMA_DEFINITIONS_INJECTION_KEY,
    UPDATE_YAML_FUNCTION_INJECTION_KEY,
} from "../injectionKeys"

const FALLBACK_NAMESPACE = "company.team"

export interface BlockEditorProvideContext {
    props: NoCodeProps
    flowYaml: ComputedRef<string>
    validationIssuesByTask: ComputedRef<Map<string, string[]>>
    inlineEditPanel: Ref<unknown>
    createTask: (parentPath: string, refPath: number | undefined, anchorEl?: HTMLElement) => void
    editTask: (parentPath: string, blockSchemaPath: string, refPath: number | undefined, split?: boolean) => void
    closeTask: () => void
    updateYaml: (yaml: string) => void
}

export function useBlockEditorProvides(ctx: BlockEditorProvideContext) {
    const flowStore = useFlowStore()
    const pluginsStore = usePluginsStore()
    const {props} = ctx

    provide(FULL_SOURCE_INJECTION_KEY, ctx.flowYaml)
    provide(BLOCK_VALIDATION_ISSUES_INJECTION_KEY, ctx.validationIssuesByTask)
    provide(PARENT_PATH_INJECTION_KEY, props.parentPath ?? "")
    provide(REF_PATH_INJECTION_KEY, props.refPath)
    provide(PANEL_INJECTION_KEY, ctx.inlineEditPanel)
    provide(POSITION_INJECTION_KEY, props.position ?? "after")
    provide(CREATING_FLOW_INJECTION_KEY, flowStore.isCreating ?? false)
    provide(DEFAULT_NAMESPACE_INJECTION_KEY, computed(() => flowStore.flow?.namespace ?? defaultNamespace() ?? FALLBACK_NAMESPACE))
    provide(CREATING_TASK_INJECTION_KEY, props.creatingTask ?? false)
    provide(EDITING_TASK_INJECTION_KEY, props.editingTask ?? false)
    provide(FIELDNAME_INJECTION_KEY, props.fieldName)
    provide(BLOCK_SCHEMA_PATH_INJECTION_KEY, computed(() => props.blockSchemaPath ?? pluginsStore.flowSchema?.$ref ?? ""))
    provide(FULL_SCHEMA_INJECTION_KEY, computed(() => pluginsStore.flowSchema ?? {}))
    provide(ROOT_SCHEMA_INJECTION_KEY, computed(() => pluginsStore.flowRootSchema ?? {}))
    provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => pluginsStore.flowDefinitions ?? {}))
    provide(CREATE_TASK_FUNCTION_INJECTION_KEY, (parentPath, _blockSchemaPath, refPath, anchorEl) => {
        ctx.createTask(parentPath, refPath, anchorEl)
    })
    provide(EDIT_TASK_FUNCTION_INJECTION_KEY, (parentPath, blockSchemaPath, refPath, split) => {
        ctx.editTask(parentPath, blockSchemaPath, refPath, split)
    })
    provide(CLOSE_TASK_FUNCTION_INJECTION_KEY, () => ctx.closeTask())
    provide(UPDATE_YAML_FUNCTION_INJECTION_KEY, (yaml: string) => ctx.updateYaml(yaml))
}
