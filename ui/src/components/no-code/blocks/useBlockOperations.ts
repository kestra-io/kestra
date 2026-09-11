import {nextTick, ref, type Ref} from "vue"
import * as flowYamlUtils from "@kestra-io/topology/flow-yaml-utils"
import {KsMessageBox} from "@kestra-io/design-system"
import {displayTaskOf, moveBlockAtPath, type BlockSection} from "../../../utils/flowableBlockOps"
import {ALL_SECTIONS, parentPathFromLaneSentinel, sectionFromSentinel} from "./blockSections"
import type {CanvasFocusApi} from "./useCanvasFocus"

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
        if (ctx.selectedPath.value) {
            const blockYaml = flowYamlUtils.extractBlockWithPath({source: ctx.flowYaml.value, path: ctx.selectedPath.value})
            const item = blockYaml ? flowYamlUtils.parse<Record<string, unknown>>(blockYaml) : undefined
            return item ? displayTaskOf(item) : undefined
        }
        const section = sectionOfSelected(id)
        return section ? ctx.sectionList(section).find(item => String(item.id) === id) : undefined
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
        moveFocused,
        moveSelected,
    }
}
