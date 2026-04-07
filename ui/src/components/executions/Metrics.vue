<template>
    <el-dropdown-item
        :icon="ChartAreaspline"
        @click="redirect"
    >
        {{ $t('metrics') }}
    </el-dropdown-item>
</template>

<script setup lang="ts">
    import {useRouter} from "vue-router";
    import ChartAreaspline from "vue-material-design-icons/ChartAreaspline.vue";
    import type {Execution} from "../../stores/executions";

    const props = defineProps<{
        taskRun: Record<string, any>;
        execution: Execution;
    }>();

    const router = useRouter();

    const redirect = () => {
        router.push({
            name: "executions/update",
            params: {
                namespace: props.execution.namespace,
                flowId: props.execution.flowId,
                id: props.execution.id,
                tab: "metrics"
            },
            query: {
                q: props.taskRun.id
            }
        });
    };
</script>