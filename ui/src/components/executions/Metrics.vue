<template>
    <KsDropdownItem
        :icon="ChartAreaspline"
        @click="onClick"
    >
        {{ $t('metrics') }}
    </KsDropdownItem>

    <KsDrawer
        v-if="isOpen"
        v-model="isOpen"
        :title="$t('metrics')"
    >
        <MetricsTable
            ref="table"
            :taskRunId="props.taskRun.id"
            :execution="props.execution"
        />
    </KsDrawer>
</template>

<script setup lang="ts">
    import {ref, nextTick} from "vue"
    import ChartAreaspline from "vue-material-design-icons/ChartAreaspline.vue"
    import MetricsTable from "./MetricsTable.vue"
    import {Execution} from "../../stores/executions"

    const props = defineProps<{
        embed?: boolean;
        taskRun: Record<string, any>;
        execution: Execution;
    }>()

    const isOpen = ref(false)
    const table = ref<InstanceType<typeof MetricsTable> | null>(null)

    const onClick = async () => {
        isOpen.value = !isOpen.value
        await nextTick()
        table.value?.loadData()
    }
</script>
