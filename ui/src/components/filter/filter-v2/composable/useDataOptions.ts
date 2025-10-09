import {ref, computed, watch} from "vue";

interface TableOptions {
    chart?: { shown?: boolean; value?: boolean; callback?: (value: boolean) => void };
    columns?: { shown?: boolean };
    refresh?: { shown?: boolean; callback?: () => void };
}

export function useDataOptions(options: TableOptions) {
    const showOptions = ref(false);

    const chartVisible = ref(options.chart?.value ?? false);

    watch(() => options.chart?.value, (newValue) => {
        if (newValue !== undefined) {
            chartVisible.value = newValue;
        }
    });

    const toggleOptions = () => {
        showOptions.value = !showOptions.value;
    };

    const updateChart = (value: boolean) => {
        chartVisible.value = value;
        options.chart?.callback?.(value);
    };

    const refreshData = () => {
        options.refresh?.callback?.();
    };

    return {
        showOptions: computed(() => showOptions.value),
        chartVisible: computed(() => chartVisible.value),
        toggleOptions,
        updateChart,
        refreshData
    };
}