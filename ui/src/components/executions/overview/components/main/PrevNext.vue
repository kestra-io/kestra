<!-- this.hasPreviousExecution = currentIndex < executions.length - 1;
                // Next means we can go to newer executions.
                this.hasNextExecution = currentIndex > 0; -->
<template>
    <div id="buttons">
        <el-button
            :disabled="currentIdx + 1 === results.length"
            @click="navigateToExecution('previous')"
        >
            <el-icon class="el-icon--left">
                <ChevronLeft />
            </el-icon>
            Older date
        </el-button>

        <span>
            {{ currentIdx !== undefined ? currentIdx + 1 : "unknown" }} /
            {{ results.length }}</span>

        <el-button
            :disabled="currentIdx === 0"
            @click="navigateToExecution('next')"
        >
            Newer date
            <el-icon class="el-icon--right">
                <ChevronRight />
            </el-icon>
        </el-button>
    </div>
</template>

<script setup lang="ts">
    import {onMounted, ref} from "vue";
    import {useRouter} from "vue-router";
    const router = useRouter();

    import {
        Execution,
        useExecutionsStore,
    } from "../../../../../stores/executions";
    const store = useExecutionsStore();

    import {createLink} from "../../utils/links";

    import ChevronLeft from "vue-material-design-icons/ChevronLeft.vue";
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue";

    const props = defineProps<{ execution: Execution }>();

    const results = ref<Execution[]>([]);

    const currentIdx = ref<number>(0);

    const loadExecutions = async (params: Record<string, any>) => {
        if (!props.execution) return;

        const response = await store.findExecutions(params);

        results.value = response.results;
    };

    const navigateToExecution = async (direction: "previous" | "next") => {
        if (currentIdx.value === -1) return;

        currentIdx.value =
            direction === "previous" ? currentIdx.value + 1 : currentIdx.value - 1;

        if (currentIdx.value < 0 || currentIdx.value >= results.value.length)
            return;
        router.push(createLink("executions", results.value[currentIdx.value]));
    };

    onMounted(async () => {
        await loadExecutions({
            namespace: props.execution.namespace,
            flowId: props.execution.flowId,
            page: 1,
            size: 100,
            sort: "state.startDate:desc",
        });

        currentIdx.value = results.value.findIndex(
            (e: Execution) => e.id === props.execution.id,
        );
    });
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
