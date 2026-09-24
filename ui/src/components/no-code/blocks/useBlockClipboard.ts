import {computed, ref} from "vue"
import * as flowYamlUtils from "@kestra-io/topology/flow-yaml-utils"
import {withFreeIds, type BlockSection} from "../../../utils/flowableBlockOps"

export type ClipboardKind = "task" | "trigger"

interface ClipboardEntry {
    kind: ClipboardKind
    block: Record<string, unknown>
}

export function clipboardKindOf(section: BlockSection): ClipboardKind {
    return section === "triggers" ? "trigger" : "task"
}

const clipboard = ref<ClipboardEntry | undefined>()

async function writeToSystemClipboard(block: Record<string, unknown>): Promise<void> {
    try {
        await navigator.clipboard?.writeText(flowYamlUtils.stringify(block))
    } catch {
        // best-effort: some browsers require a user gesture or a permission we may not have
    }
}

export function useBlockClipboard() {
    const hasClipboard = computed(() => clipboard.value !== undefined)

    function copy(section: BlockSection, block: Record<string, unknown>): void {
        const snapshot = flowYamlUtils.parse<Record<string, unknown>>(flowYamlUtils.stringify(block))
        if (!snapshot) return
        clipboard.value = {kind: clipboardKindOf(section), block: snapshot}
        void writeToSystemClipboard(snapshot)
    }

    function canPasteInto(section: BlockSection): boolean {
        return clipboard.value?.kind === clipboardKindOf(section)
    }

    function pasteFor(section: BlockSection, existingIds: Set<string>): Record<string, unknown> | undefined {
        if (!clipboard.value || !canPasteInto(section)) return undefined
        return withFreeIds(clipboard.value.block, existingIds)
    }

    return {hasClipboard, copy, canPasteInto, pasteFor}
}

export type BlockClipboardApi = ReturnType<typeof useBlockClipboard>
