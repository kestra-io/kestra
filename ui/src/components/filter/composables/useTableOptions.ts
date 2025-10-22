import {ref, computed, watch} from "vue";
import {TableOptions} from "../utils/filterTypes";

export function useTableOptions(options: TableOptions) {
    const showOptions = ref(false);
    const chartVisible = ref(options.chart?.value ?? false);

    watch(() => options.chart?.value, (newValue) => {
        if (newValue !== undefined)
            chartVisible.value = newValue;
    });

    const toggleOptions = () => showOptions.value = !showOptions.value;

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