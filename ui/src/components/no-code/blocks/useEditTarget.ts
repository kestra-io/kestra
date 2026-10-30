import {computed, type Ref} from "vue"
import * as flowYamlUtils from "@kestra-io/topology/flow-yaml-utils"
import {displayTaskOf, taskEditPathFor} from "../../../utils/flowableBlockOps"
import type {Crumb} from "../utils/useFieldNavigation"

function parseBlock(source: string, path: string): Record<string, unknown> | undefined {
    const blockYaml = flowYamlUtils.extractBlockWithPath({source, path})
    if (!blockYaml) return undefined
    try {
        return flowYamlUtils.parse<Record<string, unknown>>(blockYaml)
    } catch {
        return undefined
    }
}

export function resolveTaskEditPath(source: string, itemPath: string): string {
    if (!itemPath) return itemPath
    const item = parseBlock(source, itemPath)
    return item ? taskEditPathFor(itemPath, item) : itemPath
}

export function taskCrumbAt(source: string, itemPath: string): Crumb {
    const item = parseBlock(source, resolveTaskEditPath(source, itemPath))
    const id = item ? displayTaskOf(item).id : undefined
    return {path: itemPath, label: id != null ? String(id) : itemPath}
}

export function useEditTarget(
    source: Ref<string>,
    itemPath: Ref<string>,
    resolvesWrapper: Ref<boolean>,
    exposesContent: Ref<boolean>,
) {
    const path = computed<string>(() =>
        resolvesWrapper.value ? resolveTaskEditPath(source.value, itemPath.value) : itemPath.value,
    )

    const data = computed<Record<string, unknown> | undefined>(() =>
        exposesContent.value && path.value ? parseBlock(source.value, path.value) : undefined,
    )

    const raw = computed<string | undefined>(() =>
        exposesContent.value && path.value
            ? flowYamlUtils.extractBlockWithPath({source: source.value, path: path.value}) || undefined
            : undefined,
    )

    return {path, data, raw}
}
