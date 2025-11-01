<template>
    <el-dropdown-item
        :icon="ChartAreaspline"
        @click="onClick"
    >
        {{ $t('metrics') }}
    </el-dropdown-item>

    <Drawer
        v-if="isOpen"
        v-model="isOpen"
        :title="$t('metrics')"
    >
        <MetricsTable
            ref="table"
            :taskRunId="props.taskRun.id"
            :execution="props.execution"
        />
    </Drawer>
</template>

<script setup lang="ts">
    import {ref, nextTick} from "vue"
    import ChartAreaspline from "vue-material-design-icons/ChartAreaspline.vue"
    import Drawer from "../Drawer.vue"
    import MetricsTable from "./MetricsTable.vue"

    const props = defineProps<{
        embed?: boolean;
        taskRun: Record<string, any>;
        execution: Record<string, any>;
    }>();

    const isOpen = ref(false)
    const table = ref<InstanceType<typeof MetricsTable> | null>(null)

    const onClick = async () => {
        isOpen.value = !isOpen.value
        await nextTick()
        table.value?.loadData()
    }
</script>
