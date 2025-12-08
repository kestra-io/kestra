<template>
    <div id="buttons">
        <el-button
            :icon="ChevronLeft"
            :disabled="prevDisabled"
            @click="navigate('previous')"
        >
            {{ $t("prev_execution") }}
        </el-button>

        <el-button :disabled="nextDisabled" @click="navigate('next')">
            {{ $t("next_execution") }}
            <el-icon class="el-icon--right">
                <ChevronRight />
            </el-icon>
        </el-button>
    </div>
</template>

<script setup lang="ts">
    import {onMounted, computed, ref} from "vue";

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

    const currentPage = ref(1);

    const total = ref(0);
    const results = ref<Execution[]>([]);

    const currentIdx = ref(-1);

    const prevDisabled = computed(
        () => !!(total.value && currentIdx.value + 1 === total.value),
    );
    const nextDisabled = computed(() => !!(total.value && currentIdx.value === 0));

    const loadExecutions = async () => {
        const params = {
            "filters[namespace][PREFIX]": props.execution.namespace,
            "filters[flowId][EQUALS]": props.execution.flowId,
            "filters[timeRange][EQUALS]": "P365D", // Extended to 365 days for better navigation
            page: currentPage.value,
            size: 100,
            sort: "state.startDate:desc",
        };

        const response = await store.findExecutions(params);

        total.value = response.total;
        results.value.push(...response.results);

        currentIdx.value = results.value.findIndex(
            (e: Execution) => e.id === props.execution.id,
        );

        // If not found and more pages exist, load next page
        if (currentIdx.value === -1 && results.value.length < total.value) {
            currentPage.value += 1;
            await loadExecutions();
        }

        // If found, move router
        if (currentIdx.value !== -1) {
            router.push(createLink("executions", results.value[currentIdx.value]));
        }
    };

    const navigate = async (direction: "previous" | "next") => {
        if (currentIdx.value === -1) return;

        if (direction === "previous") {
            if (prevDisabled.value) return;
            currentIdx.value += 1;
        } else {
            if (nextDisabled.value) return;
            currentIdx.value -= 1;
        }

        // If we reached the end of loaded data but not total, load new page
        if (
            currentIdx.value >= results.value.length - 1 &&
            results.value.length < total.value
        ) {
            currentPage.value += 1;
            await loadExecutions();
        } else {
            router.push(createLink("executions", results.value[currentIdx.value]));
        }
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
