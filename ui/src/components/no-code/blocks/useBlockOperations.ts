import {nextTick, ref, type Ref} from "vue"
import * as flowYamlUtils from "@kestra-io/topology/flow-yaml-utils"
import {KsMessageBox} from "@kestra-io/design-system"
import {
    addBlockAtPath,
    collectAllIds,
    displayTaskOf,
    isWrapperLane,
    moveBlockAtPath,
    wrapAsDagTask,
    type BlockSection,
} from "../../../utils/flowableBlockOps"
import {ALL_SECTIONS, parentPathFromLaneSentinel, sectionFromParentPath, sectionFromSentinel} from "./blockSections"
import type {CanvasFocusApi} from "./useCanvasFocus"
import type {BlockClipboardApi} from "./useBlockClipboard"
import {trackAuthoringAction} from "../../../utils/tabTracking"

const CONFIRM_DIALOG_ESCAPE_GRACE_MS = 100

type Translate = (key: string, named?: Record<string, unknown>) => string

export interface BlockOperationsContext {
    t: Translate
    flowYaml: Ref<string>
    applyYaml: (yaml: string) => void
    focus: CanvasFocusApi
    selectedId: Ref<string | undefined>
    selectedPath: Ref<string | undefined>
    sectionList: (section: BlockSection) => Record<string, unknown>[]
    isFlowable: (block: Record<string, unknown>) => boolean
    deleteInSection: (section: BlockSection, id: unknown) => void
    deleteAtPath: (path: string) => void
    duplicateInSection: (section: BlockSection, id: unknown) => void
    duplicateAtPath: (path: string) => void
    clipboard: BlockClipboardApi
}

interface PasteTarget {
    section: BlockSection
    parentPath: string
    refIndex: number
}

function pathParentAndIndex(path: string): {parentPath: string; index: number} | undefined {
    const match = path.match(/^(.*)\[(\d+)\]$/)
    return match ? {parentPath: match[1], index: parseInt(match[2], 10)} : undefined
}

function blockDataAtPath(source: string, path: string): Record<string, unknown> | undefined {
    const blockYaml = flowYamlUtils.extractBlockWithPath({source, path})
    const item = blockYaml ? flowYamlUtils.parse<Record<string, unknown>>(blockYaml) : undefined
    return item ? displayTaskOf(item) : undefined
}

export function useBlockOperations(ctx: BlockOperationsContext) {
    const {t, focus} = ctx
    const confirmDialogOpen = ref(false)
    let lastConfirmDialogCloseAt = 0

    function isConfirmDialogHoldingEscape(): boolean {
        return confirmDialogOpen.value
            || performance.now() - lastConfirmDialogCloseAt < CONFIRM_DIALOG_ESCAPE_GRACE_MS
    }

    function confirmDelete(name: string, isFlowableBlock: boolean, onConfirm: () => void) {
        const message = isFlowableBlock
            ? t("block_editor.confirm_delete.message_group", {name})
            : t("block_editor.confirm_delete.message", {name})
        confirmDialogOpen.value = true
        KsMessageBox.confirm(message, t("block_editor.confirm_delete.title", {name}), {
            type: "warning",
            confirmButtonText: t("block_editor.delete"),
            cancelButtonText: t("cancel"),
        }).then(onConfirm).catch(() => {}).finally(() => {
            confirmDialogOpen.value = false
            lastConfirmDialogCloseAt = performance.now()
        })
    }

    function requestDeleteFocused() {
        const id = focus.focusedId.value
        if (!id) return
        if (sectionFromSentinel(id) || parentPathFromLaneSentinel(id)) return
        confirmDelete(focus.focusedBlockDisplayName(), focus.focusedBlockIsFlowable(), () => {
            const cards = focus.navigableCards()
            const current = cards.find(el => el.getAttribute("data-block-id") === id)
            const index = current ? cards.indexOf(current) : -1
            const neighbor = cards.slice(index + 1).find(el => !current?.contains(el)) ?? cards[index - 1]
            focus.actionInFocused("[data-test='block-card-delete']")
            focus.focusCanvasCard(neighbor?.getAttribute("data-block-id") ?? undefined)
        })
    }

    function sectionOfSelected(id: string): BlockSection | undefined {
        return ALL_SECTIONS.find(section => ctx.sectionList(section).some(item => String(item.id) === id))
    }

    function selectedBlockData(): Record<string, unknown> | undefined {
        const id = ctx.selectedId.value
        if (!id) return undefined
        if (ctx.selectedPath.value) return blockDataAtPath(ctx.flowYaml.value, ctx.selectedPath.value)
        const section = sectionOfSelected(id)
        return section ? ctx.sectionList(section).find(item => String(item.id) === id) : undefined
    }

    function selectedSection(): BlockSection | undefined {
        const id = ctx.selectedId.value
        if (!id) return undefined
        const path = ctx.selectedPath.value
        if (path) return sectionFromParentPath(pathParentAndIndex(path)?.parentPath ?? "")
        return sectionOfSelected(id)
    }

    function deleteSelected() {
        const id = ctx.selectedId.value
        if (!id) return
        if (ctx.selectedPath.value) {
            ctx.deleteAtPath(ctx.selectedPath.value)
            return
        }
        const section = sectionOfSelected(id)
        if (section) ctx.deleteInSection(section, id)
    }

    function requestDeleteSelected() {
        const id = ctx.selectedId.value
        const data = selectedBlockData()
        if (!id || !data) return
        confirmDelete(id, ctx.isFlowable(data), deleteSelected)
    }

    function duplicateSelected() {
        const id = ctx.selectedId.value
        if (!id) return
        if (ctx.selectedPath.value) {
            ctx.duplicateAtPath(ctx.selectedPath.value)
            return
        }
        const section = sectionOfSelected(id)
        if (section) ctx.duplicateInSection(section, id)
    }

    function focusedBlockContext(): {section: BlockSection; path: string; data: Record<string, unknown>} | undefined {
        const domId = focus.focusedId.value
        if (!domId || sectionFromSentinel(domId) || parentPathFromLaneSentinel(domId)) return undefined
        const path = focus.focusedBlockPath()
        const located = path ? pathParentAndIndex(path) : undefined
        if (!path || !located) return undefined
        const data = blockDataAtPath(ctx.flowYaml.value, path)
        if (!data) return undefined
        return {section: sectionFromParentPath(located.parentPath), path, data}
    }

    function copySelected() {
        const data = selectedBlockData()
        const section = selectedSection()
        if (data && section) ctx.clipboard.copy(section, data)
    }

    function copyFocusedOrSelected() {
        const focused = focusedBlockContext()
        if (focused) {
            ctx.clipboard.copy(focused.section, focused.data)
            return
        }
        copySelected()
    }

    function cutSelected() {
        const data = selectedBlockData()
        const section = selectedSection()
        if (!data || !section) return
        ctx.clipboard.copy(section, data)
        deleteSelected()
    }

    function cutFocusedOrSelected() {
        const focused = focusedBlockContext()
        if (focused) {
            ctx.clipboard.copy(focused.section, focused.data)
            ctx.deleteAtPath(focused.path)
            return
        }
        cutSelected()
    }

    /** Where a paste would land: after the focused/selected block, or at the end of a focused section or lane. */
    function pasteTarget(): PasteTarget | undefined {
        const domId = focus.focusedId.value
        const sentinelSection = sectionFromSentinel(domId)
        if (sentinelSection) return {section: sentinelSection, parentPath: sentinelSection, refIndex: -1}

        const laneParentPath = parentPathFromLaneSentinel(domId)
        if (laneParentPath) return {section: sectionFromParentPath(laneParentPath), parentPath: laneParentPath, refIndex: -1}

        const focused = focusedBlockContext()
        if (focused) {
            const located = pathParentAndIndex(focused.path)
            if (located) return {section: focused.section, parentPath: located.parentPath, refIndex: located.index}
        }

        const id = ctx.selectedId.value
        if (!id) return undefined
        const path = ctx.selectedPath.value
        if (path) {
            const located = pathParentAndIndex(path)
            return located ? {section: sectionFromParentPath(located.parentPath), parentPath: located.parentPath, refIndex: located.index} : undefined
        }
        const section = sectionOfSelected(id)
        if (!section) return undefined
        const index = ctx.sectionList(section).findIndex(item => String(item.id) === id)
        return index >= 0 ? {section, parentPath: section, refIndex: index} : undefined
    }

    function canPasteHere(): boolean {
        const target = pasteTarget()
        return target !== undefined && ctx.clipboard.canPasteInto(target.section)
    }

    function pasteRelative(): boolean {
        const target = pasteTarget()
        if (!target) return false
        const existingIds = collectAllIds(ctx.flowYaml.value)
        const block = ctx.clipboard.pasteFor(target.section, existingIds)
        if (!block) return false

        const blockToInsert = isWrapperLane(ctx.flowYaml.value, target.parentPath) ? wrapAsDagTask(block) : block
        const inserted = addBlockAtPath(ctx.flowYaml.value, target.parentPath, blockToInsert, target.refIndex, "after")
        ctx.applyYaml(inserted)
        trackAuthoringAction("task_added", "no_code", {task_type: block.type as string | undefined, position: "after"})
        focus.focusCanvasCard(String(block.id))
        return true
    }

    function moveFocused(direction: "up" | "down") {
        const path = focus.focusedBlockPath()
        if (!path) return
        const newYaml = moveBlockAtPath(ctx.flowYaml.value, path, direction)
        if (newYaml === ctx.flowYaml.value) return
        ctx.applyYaml(newYaml)
        nextTick(() => focus.focusedCard()?.scrollIntoView({block: "nearest"}))
    }

    function moveSelected(direction: "up" | "down") {
        const id = ctx.selectedId.value
        if (!id) return
        const path = ctx.selectedPath.value
        if (!path) {
            const section = sectionOfSelected(id)
            if (!section) return
            const index = ctx.sectionList(section).findIndex(item => String(item.id) === id)
            if (index < 0) return
            ctx.applyYaml(moveBlockAtPath(ctx.flowYaml.value, `${section}[${index}]`, direction))
            return
        }
        const newYaml = moveBlockAtPath(ctx.flowYaml.value, path, direction)
        if (newYaml === ctx.flowYaml.value) return
        const match = path.match(/^(.*)\[(\d+)\]$/)
        if (match) {
            const currentIndex = parseInt(match[2], 10)
            ctx.selectedPath.value = `${match[1]}[${direction === "up" ? currentIndex - 1 : currentIndex + 1}]`
        }
        ctx.applyYaml(newYaml)
    }

    return {
        confirmDialogOpen,
        isConfirmDialogHoldingEscape,
        requestDeleteFocused,
        requestDeleteSelected,
        duplicateSelected,
        copyFocusedOrSelected,
        cutFocusedOrSelected,
        canPasteHere,
        pasteRelative,
        moveFocused,
        moveSelected,
    }
}
