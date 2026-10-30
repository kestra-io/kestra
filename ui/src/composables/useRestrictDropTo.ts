import {getCurrentInstance, onUnmounted, ref, type Ref} from "vue"
import {useEventListener} from "@vueuse/core"

export function useRestrictDropTo(container: Ref<HTMLElement | undefined>) {
    const isOutside = ref(false)
    let release: (() => void) | undefined

    function refuse(event: DragEvent) {
        const target = event.target

        if (target instanceof Node && container.value?.contains(target)) {
            isOutside.value = false
            return
        }

        isOutside.value = true

        event.stopPropagation()
        event.preventDefault()

        if (event.dataTransfer) {
            event.dataTransfer.dropEffect = "none"
        }
    }

    function stop() {
        isOutside.value = false
        release?.()
        release = undefined
    }

    function start() {
        stop()

        const stops = [
            useEventListener(document, ["dragover", "dragenter"], refuse, {capture: true}),
            useEventListener(document, "dragend", stop, {capture: true}),
        ]
        release = () => stops.forEach((removeListener) => removeListener())
    }

    if (getCurrentInstance()) {
        onUnmounted(stop)
    }

    return {start, stop, isOutside}
}
