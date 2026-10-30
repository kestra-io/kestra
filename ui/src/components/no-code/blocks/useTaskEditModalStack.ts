import {computed, ref} from "vue"

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

/**
 * The stack of `TaskEditModal` targets: pushing drills into a nested block (e.g. a Dag's inner
 * task), popping walks back out. Shared by every surface that opens the modal — the No-code
 * canvas and Topology alike — so a nested edit behaves identically from either one.
 */
export function useTaskEditModalStack() {
    const modalStack = ref<ModalTarget[]>([])
    const modalTarget = computed<ModalTarget | undefined>(() => modalStack.value[modalStack.value.length - 1])

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

    return {modalStack, modalTarget, pushModalTarget, resolveCreatedTarget, popModalTo, closeModal}
}
