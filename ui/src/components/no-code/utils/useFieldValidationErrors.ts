import {computed, type ComputedRef} from "vue"
import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
import {useFlowStore} from "../../../stores/flow"
import {errorsByFieldPath} from "../../../utils/validationErrors"
import {errorsToShow} from "../../../utils/flowableBlockOps"
import type {ValidationError} from "@kestra-io/kestra-sdk"

export const FLOW_ROOT_PATH = ""

export function blockPath(parentPath?: string, refPath?: number): string | undefined {
    if (!parentPath) return undefined
    return refPath == null ? parentPath : `${parentPath}[${refPath}]`
}

/** The errors the editor is allowed to show right now — see errorsToShow. */
export function useShownValidationErrors(): ComputedRef<ValidationError[]> {
    const flowStore = useFlowStore()
    return computed(() => errorsToShow(
        flowStore.flowValidation?.errors,
        flowStore.flowParsed,
        flowStore.flowYamlOrigin,
        flowStore.saveAttempted,
    ))
}

export function useFieldValidationErrors(path: () => string | undefined): ComputedRef<Map<string, string[]>> {
    const shown = useShownValidationErrors()
    return computed(() => {
        const at = path()
        return at === undefined
            ? new Map<string, string[]>()
            : errorsByFieldPath(shown.value, YAML_UTILS.parsePath(at))
    })
}
