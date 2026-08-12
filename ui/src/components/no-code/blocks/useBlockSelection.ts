import {computed, nextTick, ref, watch, type Ref} from "vue"
import {flowYamlUtils} from "@kestra-io/topology"
import {displayTaskOf, type BlockSection} from "../../../utils/flowableBlockOps"
import {sectionFromParentPath} from "./blockSections"
import {opensInModalByDefault} from "./taskEditMode"

export interface ModalTarget {
    parentPath: string
    blockSchemaPath: string
    refPath?: number
    /** Builds a new entry at parentPath; cleared once it exists and the modal edits it. */
    creating?: boolean
}

export function modalItemPathOf(target: ModalTarget): string {
    return target.refPath !== undefined ? `${target.parentPath}[${target.refPath}]` : target.parentPath
}

export interface BlockSelectionContext {
    selectedId: Ref<string | undefined>
    editorEl: Ref<HTMLElement | undefined>
    flowYaml: Ref<string>
    flowSchemaRoot: Ref<string>
    sectionList: (section: BlockSection) => Record<string, unknown>[]
    onSelectedIdChange: (id: string | undefined) => void
    onEditTask: (parentPath: string, blockSchemaPath: string, refPath: number | undefined, split?: boolean) => void
    onCloseTask: () => void
}

export function useBlockSelection(ctx: BlockSelectionContext) {
    const internalSelectedId = ref<string | undefined>(ctx.selectedId.value)

    const activeSelectedId = computed({
        get: () => internalSelectedId.value,
        set: (id: string | undefined) => {
            internalSelectedId.value = id
            ctx.onSelectedIdChange(id)
        },
    })

    const activeSelectedPath = ref<string | undefined>()
    const modalStack = ref<ModalTarget[]>([])
    const modalTarget = computed<ModalTarget | undefined>(() => modalStack.value[modalStack.value.length - 1])

    watch(ctx.selectedId, async (id) => {
        internalSelectedId.value = id
        if (!id || !ctx.editorEl.value) return
        await nextTick()
        const card = ctx.editorEl.value.querySelector(`[data-block-id="${id}"]`) as HTMLElement | null
        const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches
        card?.scrollIntoView({block: "nearest", behavior: reduceMotion ? "auto" : "smooth"})
    })

    function blockSchemaPathFor(section: BlockSection): string {
        return [ctx.flowSchemaRoot.value, "properties", section, "items"].join("/")
    }

    function openTarget(parentPath: string, section: BlockSection, refPath: number, split: boolean) {
        if (!split && opensInModalByDefault()) {
            modalStack.value = [{parentPath, blockSchemaPath: blockSchemaPathFor(section), refPath}]
        } else {
            ctx.onEditTask(parentPath, blockSchemaPathFor(section), refPath, split)
        }
    }

    function pushModalTarget(target: ModalTarget) {
        modalStack.value = [...modalStack.value, target]
    }

    /** The entry now exists, so the top of the stack stops building it and starts editing it. */
    function resolveCreatedTarget(parentPath: string, blockSchemaPath: string, refPath: number | undefined) {
        if (!modalStack.value.length) return
        modalStack.value = [
            ...modalStack.value.slice(0, -1),
            {parentPath, blockSchemaPath, refPath},
        ]
    }

    function popModalTo(index: number) {
        modalStack.value = modalStack.value.slice(0, index + 1)
    }

    function closeModal() {
        modalStack.value = []
    }

    function selectBlock(section: BlockSection, block: Record<string, unknown>, split = false) {
        const id = block.id != null ? String(block.id) : undefined
        if (!id) return
        const index = ctx.sectionList(section).findIndex(item => item === block)
        if (index < 0) return
        activeSelectedId.value = id
        activeSelectedPath.value = undefined
        openTarget(section, section, index, split)
    }

    function openNestedEdit(itemPath: string, split = false) {
        const itemYaml = flowYamlUtils.extractBlockWithPath({source: ctx.flowYaml.value, path: itemPath})
        if (!itemYaml) return
        const item = flowYamlUtils.parse<Record<string, unknown>>(itemYaml)
        if (!item) return

        const parsed = displayTaskOf(item)
        if (!parsed?.id) return

        const match = itemPath.match(/^(.*)\[(\d+)\]$/)
        if (!match) return
        const parentPath = match[1]
        activeSelectedId.value = String(parsed.id)
        activeSelectedPath.value = itemPath
        openTarget(parentPath, sectionFromParentPath(parentPath), parseInt(match[2], 10), split)
    }

    function deselectIfCurrent(id: string) {
        if (activeSelectedId.value !== id) return
        activeSelectedId.value = undefined
        activeSelectedPath.value = undefined
        ctx.onCloseTask()
    }

    return {
        activeSelectedId,
        activeSelectedPath,
        modalStack,
        modalTarget,
        blockSchemaPathFor,
        selectBlock,
        openNestedEdit,
        deselectIfCurrent,
        pushModalTarget,
        resolveCreatedTarget,
        popModalTo,
        closeModal,
    }
}
