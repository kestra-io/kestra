import {watch, nextTick, ref, Ref, onActivated} from "vue"
import {useScroll, useThrottleFn, useWindowScroll} from "@vueuse/core"
import {storageKeys} from "../utils/constants"

export function useScrollMemory(keyRef: Ref<string>, elementRef?: Ref<HTMLElement | null>, useWindow = false): {
    saveData: (value: any, suffix?: string) => void;
    loadData: <T = any>(suffix?: string, defaultValue?: T) => T | undefined;
} {
    const getStorageKey = (suffix = "") => `${storageKeys.SCROLL_MEMORY_PREFIX}-${keyRef.value}${suffix}`

    const saveToStorage = (value: any, suffix = "") => {
        sessionStorage?.setItem(getStorageKey(suffix), JSON.stringify(value))
    }

    const loadFromStorage = <T = any>(suffix = "", defaultValue?: T): T | undefined => {
        const saved = sessionStorage?.getItem(getStorageKey(suffix))
        return saved ? JSON.parse(saved) : defaultValue
    }

    const saveScroll = (value: number) => saveToStorage(value)
    const loadScroll = (): number => loadFromStorage("", 0) || 0

    const restoreScroll = () => {
        const scrollTop = loadScroll()
        const applyScroll = useWindow
            ? () => window.scrollTo({top: scrollTop, behavior: "smooth"})
            : () => { if (elementRef?.value) elementRef.value.scrollTo({top: scrollTop, behavior: "smooth"}) }
        setTimeout(applyScroll, 10)
    }
    onActivated(() => nextTick(restoreScroll))

    const throttledSave = useThrottleFn((top: number) => saveScroll(top), 100)

    if (useWindow) {
        const {y} = useWindowScroll({throttle: 16, onScroll: () => throttledSave(y.value)})
        watch(keyRef, () => nextTick(restoreScroll), {immediate: true})
    } else {
        useScroll(elementRef || ref(null), {
            throttle: 16,
            onScroll: () => { if (elementRef?.value) throttledSave(elementRef.value.scrollTop) }
        })
        watch([keyRef, () => elementRef?.value], ([newKey, newElement]) => {
            if (newElement && newKey) nextTick(restoreScroll)
        }, {immediate: true})
    }

    return {saveData: saveToStorage, loadData: loadFromStorage}
}
