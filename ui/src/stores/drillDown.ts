import {defineStore} from "pinia"
import {ref} from "vue"

export interface DrillDownTarget {
    name: string;
    query: Record<string, string>;
    timeFiltered: boolean;
}

export const useDrillDownStore = defineStore("drillDown", () => {
    const isOpen = ref(false)
    const target = ref<DrillDownTarget | null>(null)

    const open = (newTarget: DrillDownTarget) => {
        target.value = newTarget
        isOpen.value = true
    }

    const close = () => {
        isOpen.value = false
    }

    return {
        isOpen,
        target,
        open,
        close,
    }
})
