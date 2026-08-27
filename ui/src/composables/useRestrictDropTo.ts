import {getCurrentInstance, onUnmounted, ref, type Ref} from "vue"

/**
 * Refuses drops anywhere outside `container` while a drag it owns is in flight.
 *
 * A drag whose only valid destination is one widget still travels over the rest of the app, where
 * unrelated drop targets cancel dragover to accept drops of their own. That cancelling is what makes
 * the browser show a move cursor, advertising a drop that will never happen. Refusing those targets
 * one at a time only holds until the next one is added, so events are stopped in the capture phase
 * instead, before any of them sees them.
 *
 * @param container the only region allowed to accept the drag; its own drop handling is untouched
 * @return start to call when the drag begins, stop to release the app (also run on unmount), and
 *         isOutside, true while the pointer is beyond the container, for drag-source styling
 */
export function useRestrictDropTo(container: Ref<HTMLElement | undefined>) {
    const isOutside = ref(false)

    function refuse(event: DragEvent) {
        const target = event.target

        if (target instanceof Node && container.value?.contains(target)) {
            isOutside.value = false
            return
        }

        isOutside.value = true

        event.stopPropagation()

        // Refuse explicitly rather than by leaving the event alone: an uncancelled dragover means
        // "the browser's default applies", and over an editable element — Monaco's textarea, an
        // input, anything contenteditable — that default is to accept the text the drag carries.
        // Cancelling and setting the operation to none is what refuses those too.
        event.preventDefault()

        if (event.dataTransfer) {
            event.dataTransfer.dropEffect = "none"
        }
    }

    function stop() {
        isOutside.value = false
        document.removeEventListener("dragover", refuse, true)
        document.removeEventListener("dragenter", refuse, true)
        document.removeEventListener("dragend", stop, true)
    }

    function start() {
        // A drag cancelled without a dragend would otherwise leave the listeners behind
        stop()

        document.addEventListener("dragover", refuse, true)
        document.addEventListener("dragenter", refuse, true)
        document.addEventListener("dragend", stop, true)
    }

    if (getCurrentInstance()) {
        onUnmounted(stop)
    }

    return {start, stop, isOutside}
}
