import {ref, computed, watch} from "vue";
import {TableOptions} from "../utils/filterTypes";
import {storageKeys} from "../../../utils/constants";

export function useDataOptions(options: TableOptions) {
    const showOptions = ref((localStorage.getItem(storageKeys.FILTER_DATA_OPTIONS_PREFIX) ?? "false").toLowerCase() === "true");
    const chartVisible = ref(options.chart?.value ?? true);

    watch(() => options.chart?.value, (newValue) => {
        if (newValue !== undefined)
            chartVisible.value = newValue;
    });

    const toggleOptions = () => {
        showOptions.value = !showOptions.value;
        localStorage.setItem(storageKeys.FILTER_DATA_OPTIONS_PREFIX, String(showOptions.value));
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