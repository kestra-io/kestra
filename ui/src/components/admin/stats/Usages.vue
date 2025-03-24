<template>
    <el-card class="p-3 pt-2 pb-2" :header="$t('your usage')" v-if="usages">
        <el-row class="mt-1 mb-1 align-items-center">
            <AspectRatio />
            <el-text size="small">
                {{ $t("namespaces") }}
            </el-text>
            <el-divider class="m-auto" />
            <el-text size="small">
                {{ namespaces }}
            </el-text>
            <router-link :to="{name: namespaceRoute}">
                <el-button class="wh-15" :icon="TextSearchVariant" link />
            </router-link>
        </el-row>
        <el-row class="mt-1 mb-1 align-items-center">
            <FileTreeOutline />
            <el-text size="small">
                {{ $t("flows") }}
            </el-text>
            <el-divider class="m-auto" />
            <el-text size="small">
                {{ flows }}
            </el-text>
            <router-link :to="{name: 'flows/list'}">
                <el-button class="wh-15" :icon="TextSearchVariant" link />
            </router-link>
        </el-row>
        <el-row class="mt-1 mb-1 align-items-center">
            <ListBoxOutline />
            <el-text size="small">
                {{ $t("tasks") }}
            </el-text>
            <el-divider class="m-auto" />
            <el-text size="small">
                {{ tasks }}
            </el-text>
            <router-link :to="{name: 'flows/list'}">
                <el-button class="wh-15" :icon="TextSearchVariant" link />
            </router-link>
        </el-row>
        <el-row class="mt-1 mb-1 align-items-center">
            <LightningBolt />
            <el-text size="small">
                {{ $t("triggers") }}
            </el-text>
            <el-divider class="m-auto" />
            <el-text size="small">
                {{ triggers }}
            </el-text>
            <router-link :to="{name: 'admin/triggers'}">
                <el-button class="wh-15" :icon="TextSearchVariant" link />
            </router-link>
        </el-row>
        <el-row class="mt-1 mb-1 align-items-center">
            <TimelineClock />
            <el-text size="small">
                {{ $t("executions") }}
            </el-text>
            <el-divider class="m-auto" />
            <el-text size="small">
                {{ executionsOverTwoDays }} ({{ $t("last 48 hours") }})
            </el-text>
            <router-link :to="{name: 'executions/list'}">
                <el-button class="wh-15" :icon="TextSearchVariant" link />
            </router-link>
        </el-row>
        <el-row v-if="taskrunsOverTwoDays" class="mt-1 mb-1 align-items-center">
            <ChartTimeline />
            <el-text size="small">
                {{ $t("taskruns") }}
            </el-text>
            <el-divider class="m-auto" />
            <el-text size="small">
                {{ taskrunsOverTwoDays }} ({{ $t("last 48 hours") }})
            </el-text>
            <router-link :to="{name: 'taskruns/list'}">
                <el-button class="wh-15" :icon="TextSearchVariant" link />
            </router-link>
        </el-row>
        <el-row class="mt-1 mb-1 align-items-center">
            <TableClock />
            <el-text size="small">
                {{ $t("executions duration (in minutes)") }}
            </el-text>
            <el-divider class="m-auto" />
            <el-text size="small">
                {{ executionsDurationOverTwoDays }} ({{ $t("last 48 hours") }})
            </el-text>
            <router-link :to="{name: 'executions/list'}">
                <el-button class="wh-15" :icon="TextSearchVariant" link />
            </router-link>
        </el-row>
        <slot name="additional-usages" />
    </el-card>
</template>
<script setup>
    import TextSearchVariant from "vue-material-design-icons/TextSearchVariant.vue";
    import AspectRatio from "vue-material-design-icons/AspectRatio.vue";
    import FileTreeOutline from "vue-material-design-icons/FileTreeOutline.vue";
    import ListBoxOutline from "vue-material-design-icons/ListBoxOutline.vue";
    import LightningBolt from "vue-material-design-icons/LightningBolt.vue";
    import TimelineClock from "vue-material-design-icons/TimelineClock.vue";
    import ChartTimeline from "vue-material-design-icons/ChartTimeline.vue";
    import TableClock from "vue-material-design-icons/TableClock.vue";
</script>
<script>
    import {mapGetters} from "vuex";

    export default {
        data() {
            return {
                usages: undefined
            }
        },
        emits: ["loaded"],
        async beforeMount() {
            if (this.fetchedUsages) {
                this.usages = this.fetchedUsages;
            } else {
                this.usages = await this.$store.dispatch("misc/loadAllUsages");
            }

            this.$emit("loaded");
        },
        props: {
            fetchedUsages: {
                type: Object,
                default: undefined
            }
        },
        methods: {
            aggregateValues(object) {
                return this.aggregateValuesFromList(object ? Object.values(object) : object);
            },
            aggregateValuesFromList(list) {
                return this.aggregateValuesFromListWithGetter(list, (item) => item);
            },
            aggregateValuesFromListWithGetter(list, valueGetter) {
                return this.aggregateValuesFromListWithGetterAndAggFunction(list, valueGetter, list => list.reduce((a, b) => a + b, 0));
            },
            aggregateValuesFromListWithGetterAndAggFunction(list, valueGetter, aggFunction) {
                if (!list) {
                    return 0;
                }

                return aggFunction(list.map(valueGetter));
            }
        },
        computed: {
            ...mapGetters("misc", ["configs"]),
            namespaces() {
                return this.usages.flows?.namespacesCount ?? 0;
            },
            flows() {
                return this.usages.flows?.count ?? 0;
            },
            namespaceRoute() {
                try {
                    this.$router.resolve({name: "namespaces/list"})
                    return "namespaces/list";
                } catch  {
                    return "flows/list"
                }
            },
            tasks() {
                return this.aggregateValues(this.usages.flows?.taskTypeCount);
            },
            triggers() {
                return this.aggregateValues(this.usages.flows?.triggerTypeCount);
            },
            executionsPerDay() {
                return (this.usages.executions?.dailyExecutionsCount ?? [])
                    .filter(item => item.groupBy === "day");
            },
            executionsOverTwoDays() {
                return this.aggregateValuesFromListWithGetter(this.executionsPerDay, item => item.duration.count ?? 0);
            },
            taskrunsPerDay() {
                return this.usages.executions?.dailyTaskrunsCount?.filter(item => item.groupBy === "day");
            },
            taskrunsOverTwoDays() {
                if (!this.configs?.isTaskRunEnabled) {
                    return undefined;
                }

                return this.aggregateValuesFromListWithGetter(this.taskrunsPerDay, item => item.duration.count ?? 0);
            },
            executionsDurationOverTwoDays() {
                return this.aggregateValuesFromListWithGetterAndAggFunction(
                    this.executionsPerDay,
                    item => item.duration.sum ?? this.$moment.duration("PT0S"),
                    list => list.reduce((a, b) => this.$moment.duration(a).add(this.$moment.duration(b)), this.$moment.duration("PT0S"))
                ).minutes();
            }
        }
    };
</script>
<style scoped>
    .el-card {
        &:deep(.el-card__header) {
            border-bottom: 0;
        }

        .el-row, :slotted(.el-row) {
            gap: 0.5rem;
            height: 2rem;

            & .el-text--small, :slotted(&) .el-text--small {
                line-height: 1;
            }

            & .el-divider, :slotted(&) .el-divider {
                flex: 1;
            }

            & .el-button, :slotted(&) .el-button {
                color: var(--ks-content-primary);
            }
        }
    }
</style>