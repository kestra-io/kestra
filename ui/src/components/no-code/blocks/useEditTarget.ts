import {computed, type Ref} from "vue"
import {flowYamlUtils} from "@kestra-io/topology"
import {taskEditPathFor} from "../../../utils/flowableBlockOps"

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
