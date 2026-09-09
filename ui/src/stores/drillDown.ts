import {defineStore} from "pinia"
import {ref} from "vue"
import type {LocationQuery} from "vue-router"

export interface DrillDownTarget {
    name: string;
    query: LocationQuery;
    timeFiltered: boolean;
    timeWindow?: Record<string, string>;
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
