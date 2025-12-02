<template>
    <div id="buttons">
        <el-button @click="navigateToExecution('previous')">
            <el-icon class="el-icon--left">
                <ChevronLeft />
            </el-icon>
            {{ $t("prev_execution") }}
        </el-button>
        <el-button @click="navigateToExecution('next')">
            {{ $t("next_execution") }}
            <el-icon class="el-icon--right">
                <ChevronRight />
            </el-icon>
        </el-button>
    </div>
</template>

<script setup lang="ts">
    import {useRouter} from "vue-router";
    const router = useRouter();

    import {
        Execution,
        useExecutionsStore,
    } from "../../../../../stores/executions";
    const store = useExecutionsStore();

    import ChevronLeft from "vue-material-design-icons/ChevronLeft.vue";
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue";

    const props = defineProps<{ execution: Execution }>();

    const navigateToExecution = async (direction: "previous" | "next") => {
        if (!props.execution) return;

        try {
            const params = {
                namespace: props.execution.namespace,
                flowId: props.execution.flowId,
                pageSize: 100,
                sort: "state.startDate:desc",
            };

            const response = await store.findExecutions(params);
            const result = response?.results ?? [];

            if (!result.length) return;

            const currentIdx = result.findIndex(
                (e: Execution) => e.id === props.execution.id,
            );

            if (currentIdx === -1) return;

            // next = newer (-1), previous = older (+1)
            const targetIdx =
                direction === "previous" ? currentIdx + 1 : currentIdx - 1;

            if (targetIdx < 0 || targetIdx >= result.length) return;

            const target = result[targetIdx];

            router.push({
                name: "executions/update",
                params: {
                    ...(target.tenantId ? {tenant: target.tenantId} : {}),
                    namespace: target.namespace,
                    flowId: target.flowId,
                    id: target.id,
                    tab: "overview",
                },
            });
        } catch (error) {
            console.error("Failed to navigate executions:", error);
        }
    };
</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/variables";

#buttons {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: $spacer;

    .el-button {
        width: calc($spacer * 12);
        font-size: $font-size-sm;
    }
}
</style>
