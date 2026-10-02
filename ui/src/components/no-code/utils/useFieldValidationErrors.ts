import {computed, type ComputedRef} from "vue"
import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
import {useFlowStore} from "../../../stores/flow"
import {errorsByFieldPath} from "../../../utils/validationErrors"

export const FLOW_ROOT_PATH = ""

export function blockPath(parentPath?: string, refPath?: number): string | undefined {
    if (!parentPath) return undefined
    return refPath == null ? parentPath : `${parentPath}[${refPath}]`
}

export function useFieldValidationErrors(path: () => string | undefined): ComputedRef<Map<string, string[]>> {
    const flowStore = useFlowStore()
    return computed(() => {
        const at = path()
        return at === undefined
            ? new Map<string, string[]>()
            : errorsByFieldPath(flowStore.flowValidation?.errors, YAML_UTILS.parsePath(at))
    })
}
