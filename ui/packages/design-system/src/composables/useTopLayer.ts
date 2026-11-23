import {onMounted, ref, type Ref} from "vue"
import {useZIndex} from "element-plus"

/**
 * A z-index above every Element Plus overlay open so far, claimed on mount.
 *
 * Element Plus draws its overlays from one ever-climbing counter, so a fixed `--ks-z-*` token can
 * always be overtaken; taking the next value from that same counter cannot.
 */
export function useTopLayer(): Ref<number> {
    const {nextZIndex} = useZIndex()
    const zIndex = ref(0)

    onMounted(() => {
        zIndex.value = nextZIndex()
    })

    return zIndex
}
