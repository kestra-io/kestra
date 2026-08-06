<template>
    <KsSelect
        :modelValue="values"
        @update:model-value="onInput"
        filterable
        clearable
        allowCreate
    >
        <KsOption
            v-for="item in chartIds"
            :key="item"
            :label="item"
            :value="item"
        />
    </KsSelect>
</template>
<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {collapseEmptyValues} from "../utils/collapseEmptyValues"
    import {useDashboardStore} from "../../../../stores/dashboard"
    import {isExportableChart} from "../../../dashboard/composables/useDashboards"

    const props = withDefaults(defineProps<{
        modelValue?: object | string | number | boolean | unknown[]
        schema?: Record<string, unknown>
        required?: boolean
        task?: Record<string, unknown>
        root?: string
        definitions?: Record<string, unknown>
    }>(), {
        modelValue: undefined,
        schema: undefined,
        required: false,
        task: undefined,
        root: undefined,
        definitions: undefined,
    })

    const emit = defineEmits<{
        "update:modelValue": [value: unknown]
    }>()

    const dashboardStore = useDashboardStore()

    const chartIds = ref<string[]>([])

    const values = computed(() => props.modelValue ?? (props.schema as Record<string, unknown> | undefined)?.default)

    // Empty dashboardId falls back to the "_default" sentinel dashboard's charts
    const dashboardId = computed(() => (props.task?.dashboardId as string | undefined) ?? "_default")

    watch(dashboardId, async () => {
        const charts = await dashboardStore.chartsById(dashboardId.value)
        chartIds.value = charts.filter(chart => isExportableChart(chart.type)).map(chart => chart.id)
    }, {immediate: true})

    function onInput(value: unknown) {
        emit("update:modelValue", collapseEmptyValues(value))
    }
</script>
