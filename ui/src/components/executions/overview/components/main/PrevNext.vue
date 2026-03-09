<template>
    <div id="buttons">
        <el-button
            :icon="ChevronLeft"
            :disabled="!results.previous"
            @click="navigate('previous')"
        >
            {{ $t("prev_execution") }}
        </el-button>

        <el-button :disabled="!results.next" @click="navigate('next')">
            {{ $t("next_execution") }}
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

    const results = ref<{
        previous: Execution | null;
        current: Execution;
        next: Execution | null;
    }>({
        previous: null,
        current: props.execution,
        next: null,
    });

    const loadExecutions = async () => {
        const baseParams = {
            "filters[namespace][PREFIX]": props.execution.namespace,
            "filters[flowId][EQUALS]": props.execution.flowId,
            sort: "state.startDate:desc",
            size: 1,
        };

        const [newerRes, olderRes] = await Promise.all([
            // one execution AFTER (more recent than) current startDate
            store.findExecutions({
                ...baseParams,
                "filters[startDate][GREATER_THAN]": props.execution.state.startDate,
            }),
            // one execution BEFORE (older than) current startDate
            store.findExecutions({
                ...baseParams,
                "filters[startDate][LESS_THAN]": props.execution.state.startDate,
            }),
        ]);

        results.value = {
            previous: newerRes.results?.[0] ?? null,
            current: props.execution,
            next: olderRes.results?.[0] ?? null,
        };
    };

    const navigate = async (direction: "previous" | "next") => {
        if (direction === "previous" && !results.value.previous) return;
        if (direction === "next" && !results.value.next) return;

        router.push(createLink("executions", results.value[direction]!));
    };

    onMounted(async () => {
        await loadExecutions();
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
        font-size: $font-size-sm;
    }
}
</style>
