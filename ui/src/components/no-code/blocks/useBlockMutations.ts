import {type Ref} from "vue"
import * as flowYamlUtils from "@kestra-io/topology/flow-yaml-utils"
import {
    deleteBlock,
    deleteBlockAtPath,
    duplicateBlock,
    duplicateBlockAtPath,
    type BlockSection,
} from "../../../utils/flowableBlockOps"

export interface BlockMutationsContext {
    flowYaml: Ref<string>
    applyYaml: (yaml: string) => void
    deleteWithUndo: (name: string, mutate: () => void) => void
    deselectIfCurrent: (id: string) => void
}

export function useBlockMutations(ctx: BlockMutationsContext) {
    function deleteInSection(section: BlockSection, id: unknown) {
        if (typeof id !== "string") return
        ctx.deleteWithUndo(id, () => {
            const newYaml = deleteBlock(ctx.flowYaml.value, section, id)
            ctx.deselectIfCurrent(id)
            ctx.applyYaml(newYaml)
        })
    }

    function deleteAtPath(path: string) {
        const blockYaml = flowYamlUtils.extractBlockWithPath({source: ctx.flowYaml.value, path})
        const parsed = blockYaml ? flowYamlUtils.parse<Record<string, unknown>>(blockYaml) : null
        const name = parsed?.id ? String(parsed.id) : path
        ctx.deleteWithUndo(name, () => {
            const newYaml = deleteBlockAtPath(ctx.flowYaml.value, path)
            if (parsed?.id) ctx.deselectIfCurrent(String(parsed.id))
            ctx.applyYaml(newYaml)
        })
    }

    function duplicateInSection(section: BlockSection, id: unknown) {
        if (typeof id !== "string") return
        ctx.applyYaml(duplicateBlock(ctx.flowYaml.value, section, id))
    }

    function duplicateAtPath(path: string) {
        ctx.applyYaml(duplicateBlockAtPath(ctx.flowYaml.value, path))
    }

    function updateDependsOn(itemPath: string, dependsOn: string[]) {
        const newContent = dependsOn.length > 0 ? flowYamlUtils.stringify(dependsOn) : ""
        ctx.applyYaml(flowYamlUtils.replaceBlockWithPath({
            source: ctx.flowYaml.value,
            path: `${itemPath}.dependsOn`,
            newContent,
        }))
    }

    return {deleteInSection, deleteAtPath, duplicateInSection, duplicateAtPath, updateDependsOn}
}
