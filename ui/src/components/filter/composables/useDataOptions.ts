import {ref, computed, watch} from "vue";
import {TableOptions} from "../utils/filterTypes";

const LOCAL_STORAGE_KEY = "filterDataOptions";

export function useDataOptions(options: TableOptions) {
    const showOptions = ref((localStorage.getItem(LOCAL_STORAGE_KEY) ?? "false").toLowerCase() === "true");
    const chartVisible = ref(options.chart?.value ?? true);

    watch(() => options.chart?.value, (newValue) => {
        if (newValue !== undefined)
            chartVisible.value = newValue;
    });

    const toggleOptions = () => {
        showOptions.value = !showOptions.value;
        localStorage.setItem(LOCAL_STORAGE_KEY, String(showOptions.value));
    }

    const updateChart = (val: boolean) => {
        chartVisible.value = val;
        options.chart?.callback?.(val);
    };

    const refreshData = () => options.refresh?.callback?.();

    return {
        toggleOptions,
        updateChart,
        refreshData,
        showOptions: computed(() => showOptions.value),
        chartVisible: computed(() => chartVisible.value),
    };
}