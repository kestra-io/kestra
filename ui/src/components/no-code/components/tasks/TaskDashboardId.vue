<template>
    <KsSelect
        :modelValue="values"
        @update:model-value="onInput"
        filterable
        clearable
        allowCreate
    >
        <KsOption
            v-for="item in dashboards"
            :key="item.id"
            :label="item.title ?? item.id"
            :value="item.id"
        />
    </KsSelect>
</template>
<script setup lang="ts">
    import {computed, ref, onMounted} from "vue"
    import {collapseEmptyValues} from "../utils/collapseEmptyValues"
    import {useDashboardStore} from "../../../../stores/dashboard"

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

    const dashboards = ref<{ id: string; title?: string }[]>([])

    const values = computed(() => props.modelValue ?? (props.schema as Record<string, unknown> | undefined)?.default)

    onMounted(async () => {
        // "_default" is a backend/task-only sentinel resolved when dashboardId is
        // omitted; it is never a real saved dashboard, so it must never appear here.
        dashboards.value = await dashboardStore.searchIds()
    })

    function onInput(value: unknown) {
        emit("update:modelValue", collapseEmptyValues(value))
    }
</script>
