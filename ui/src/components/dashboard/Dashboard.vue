<template>
    <Header />

    <div class="dashboard">
        <el-row :gutter="20" class="mx-0">
            <el-col :xs="24" :sm="12" :lg="6">
                <Card
                    :icon="CheckBold"
                    :label="t('dashboard.success_ratio')"
                    :value="stats.success"
                    :redirect="{
                        name: 'executions/list',
                        query: {state: State.SUCCESS, scope: 'USER'},
                    }"
                />
            </el-col>
            <el-col :xs="24" :sm="12" :lg="6">
                <Card
                    :icon="Alert"
                    :label="t('dashboard.failure_ratio')"
                    :value="stats.failed"
                    :redirect="{
                        name: 'executions/list',
                        query: {state: State.FAILED, scope: 'USER'},
                    }"
                />
            </el-col>
            <el-col :xs="24" :sm="12" :lg="6">
                <Card
                    :icon="FileTree"
                    :label="t('flows')"
                    :value="numbers.flows"
                    :redirect="{
                        name: 'flows/list',
                        query: {scope: 'USER'},
                    }"
                />
            </el-col>
            <el-col :xs="24" :sm="12" :lg="6">
                <Card
                    :icon="LightningBolt"
                    :label="t('triggers')"
                    :value="numbers.triggers"
                    :redirect="{
                        name: 'admin/triggers',
                    }"
                />
            </el-col>
        </el-row>

        <el-row :gutter="20" class="mx-0">
            <el-col :xs="24" :lg="16">
                <ExecutionsBar :data="graphData" :total="stats.total" />
            </el-col>
            <el-col :xs="24" :lg="8">
                <ExecutionsDoughnut :data="graphData" :total="stats.total" />
            </el-col>
        </el-row>

        <el-row :gutter="20" class="mx-0">
            <el-col :xs="24" :lg="12">
                <div>1</div>
            </el-col>
            <el-col :xs="24" :lg="12">
                <div>2</div>
            </el-col>
        </el-row>
    </div>
</template>

<script setup>
    import {onBeforeMount, ref, computed} from "vue";
    import {useStore} from "vuex";
    import {useI18n} from "vue-i18n";

    import moment from "moment";
    import _cloneDeep from "lodash/cloneDeep";

    import {apiUrl} from "override/utils/route";
    import State from "../../utils/state";

    import Header from "./components/Header.vue";
    import Card from "./components/Card.vue";

    import ExecutionsBar from "./components/charts/ExecutionsBar.vue";
    import ExecutionsDoughnut from "./components/charts/ExecutionsDoughnut.vue";

    import CheckBold from "vue-material-design-icons/CheckBold.vue";
    import Alert from "vue-material-design-icons/Alert.vue";
    import LightningBolt from "vue-material-design-icons/LightningBolt.vue";
    import FileTree from "vue-material-design-icons/FileTree.vue";

    const store = useStore();
    const {t} = useI18n({useScope: "global"});

    const numbers = ref({flows: 0, triggers: 0});
    const fetchNumbers = () => {
        store.$http.post(`${apiUrl(store)}/stats/summary`, {}).then((response) => {
            if (!response.data) return;
            numbers.value = response.data;
        });
    };

    const executions = ref({raw: {}, all: {}, yesterday: {}, today: {}});
    const stats = computed(() => {
        const counts = executions?.value?.all?.executionCounts || {};
        const total = Object.values(counts).reduce((sum, count) => sum + count, 0);

        function percentage(count, total) {
            return total ? Math.round((count / total) * 100) : 0;
        }

        return {
            total,
            success: `${percentage(counts[State.SUCCESS] || 0, total)}%`,
            failed: `${percentage(counts[State.FAILED] || 0, total)}%`,
        };
    });
    const transformer = (data) => {
        return data.reduce((accumulator, value) => {
            if (!accumulator) accumulator = _cloneDeep(value);
            else {
                for (const key in value.executionCounts) {
                    accumulator.executionCounts[key] += value.executionCounts[key];
                }

                for (const key in value.duration) {
                    accumulator.duration[key] += value.duration[key];
                }
            }

            return accumulator;
        }, null);
    };
    const fetchExecutions = () => {
        const startDate = moment().subtract(30, "days").toISOString();
        const endDate = moment().toISOString();

        store.dispatch("stat/daily", {startDate, endDate}).then((response) => {
            const sorted = response.sort(
                (a, b) => new Date(b.date) - new Date(a.date),
            );

            executions.value = {
                raw: sorted,
                all: transformer(sorted),
                yesterday: sorted.at(-2),
                today: sorted.at(-1),
            };
        });
    };

    const graphData = computed(() => store.state.stat.daily || []);

    onBeforeMount(async () => {
        try {
            await Promise.any([fetchNumbers(), fetchExecutions()]);
        } catch (error) {
            console.error("All promises failed:", error);
        }
    });
</script>

<style lang="scss" scoped>
@import "@kestra-io/ui-libs/src/scss/variables";

$spacing: 20px;

.dashboard {
    padding: $spacing;

    & .el-row {
        width: 100%;

        & .el-col {
            padding-bottom: $spacing;

            & div {
                border-radius: $border-radius;
                background: var(--card-bg);
            }
        }
    }
}
</style>
