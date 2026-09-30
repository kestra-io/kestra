import {computed, type ComputedRef} from "vue"
import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
import {useFlowStore} from "../../../stores/flow"
import {errorsByFieldPath} from "../../../utils/validationErrors"

export function blockPointerPrefix(parentPath?: string, refPath?: number): (string | number)[] | undefined {
    if (!parentPath) return undefined
    return YAML_UTILS.parsePath(refPath == null ? parentPath : `${parentPath}[${refPath}]`)
}

export function useFieldValidationErrors(
    prefix: () => (string | number)[] | undefined,
): ComputedRef<Map<string, string[]>> {
    const flowStore = useFlowStore()
    return computed(() => {
        const at = prefix()
        return at === undefined
            ? new Map<string, string[]>()
            : errorsByFieldPath(flowStore.flowValidation?.errors, at)
    })
}
