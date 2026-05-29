import {ref, computed, watch, type ComputedRef} from "vue"
import type {TableOptions} from "../utils/filterTypes"

export function useDataOptions(options: TableOptions): {
    toggleOptions: () => void;
    updateChart: (val: boolean) => void;
    refreshData: () => void;
    showOptions: ComputedRef<boolean>;
    chartVisible: ComputedRef<boolean>;
} {
    const showOptions = ref((localStorage.getItem("filterDataOptions") ?? "false").toLowerCase() === "true")
    const chartVisible = ref(options.chart?.value ?? true)

    watch(() => options.chart?.value, (newValue) => {
        if (newValue !== undefined)
            chartVisible.value = newValue
    })

    const toggleOptions = (): void => {
        showOptions.value = !showOptions.value
        localStorage.setItem("filterDataOptions", String(showOptions.value))
    }

    const updateChart = (val: boolean): void => {
        chartVisible.value = val
        options.chart?.callback?.(val)
    }

    const refreshData = (): void => { options.refresh?.callback?.() }

    return {
        toggleOptions: toggleOptions,
        updateChart: updateChart,
        refreshData: refreshData,
        showOptions: computed(() => showOptions.value),
        chartVisible: computed(() => chartVisible.value),
    }
}
