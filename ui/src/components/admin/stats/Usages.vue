<template>
    <div v-if="usages" class="usage-card">
        <div class="usage-card-header">
            <span>{{ $t('your usage') }}</span>
            <slot name="button" />
        </div>
        <div class="usage-card-body">
            <div v-for="item in filteredUsageItems" :key="item.key" class="usage-row">
                <component :is="item.icon" class="usage-icon" />
                <el-text size="small" class="usage-label">
                    {{ $t(item.labelKey) }}
                </el-text>
                <div class="usage-divider" />
                <el-text size="small" class="usage-value">
                    {{ item.value }}
                </el-text>
                <router-link :to="{name: item.route}">
                    <el-button class="wh-15" :icon="TextSearchVariant" link />
                </router-link>
            </div>
            <slot name="additional-usages" />
        </div>
    </div>
</template>
<script setup>
    import TextSearchVariant from "vue-material-design-icons/TextSearchVariant.vue";
    import FileTreeOutline from "vue-material-design-icons/FileTreeOutline.vue";
    import LightningBolt from "vue-material-design-icons/LightningBolt.vue";
    import TimelineClockOutline from "vue-material-design-icons/TimelineClockOutline.vue";
    import ChartTimeline from "vue-material-design-icons/ChartTimeline.vue";
    import CalendarMonth from "vue-material-design-icons/CalendarMonth.vue";
    import DotsSquare from "vue-material-design-icons/DotsSquare.vue";
    import TimelineTextOutline from "vue-material-design-icons/TimelineTextOutline.vue";
</script>
<script>
    import {mapStores} from "pinia";
    import {useMiscStore} from "../../../stores/misc";

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
                this.usages = await this.miscStore.loadAllUsages();
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
            ...mapStores(useMiscStore),
            usageItems() {
                return [
                    {
                        key: "namespaces",
                        icon: DotsSquare,
                        labelKey: "namespaces",
                        value: this.namespaces,
                        route: this.namespaceRoute
                    },
                    {
                        key: "flows",
                        icon: FileTreeOutline,
                        labelKey: "flows",
                        value: this.flows,
                        route: "flows/list"
                    },
                    {
                        key: "tasks",
                        icon: TimelineTextOutline,
                        labelKey: "tasks",
                        value: this.tasks,
                        route: "flows/list"
                    },
                    {
                        key: "triggers",
                        icon: LightningBolt,
                        labelKey: "triggers",
                        value: this.triggers,
                        route: "admin/triggers"
                    },
                    {
                        key: "executions",
                        icon: TimelineClockOutline,
                        labelKey: "executions",
                        value: `${this.executionsOverTwoDays} (${this.$t("last 48 hours")})`,
                        route: "executions/list"
                    },
                    {
                        key: "taskruns",
                        icon: ChartTimeline,
                        labelKey: "taskruns",
                        value: `${this.taskrunsOverTwoDays} (${this.$t("last 48 hours")})`,
                        route: "taskruns/list"
                    },
                    {
                        key: "executionsDuration",
                        icon: CalendarMonth,
                        labelKey: "executions duration (in minutes)",
                        value: `${this.executionsDurationOverTwoDays} (${this.$t("last 48 hours")})`,
                        route: "executions/list"
                    }
                ];
            },
            filteredUsageItems() {
                return this.usageItems.filter(item => {
                    if (item.key === "taskruns") {
                        return !!this.taskrunsOverTwoDays;
                    }
                    return true;
                });
            },
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
                } catch {
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
                if (!this.miscStore.configs?.isTaskRunEnabled) {
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
<style lang="scss" scoped>
.usage-card {
    background-color: transparent;
    // min-height: 432px;
    padding: 1.25rem;
    border: 1px solid var(--ks-border-primary);
    border-radius: 8px;
    box-shadow: 0 2px 4px var(--ks-card-shadow);

    .usage-card-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        width: 100%;
        padding-bottom: 1rem;
        margin-bottom: 1rem;

        span {
            font-size: 18.4px;
            font-weight: 600;
        }
    }

    .usage-card-body {
        display: flex;
        flex-direction: column;
        gap: 0.25rem
    }

    .usage-row {
        display: flex;
        align-items: center;
        gap: 1rem;
        height: 2rem;

        .usage-icon {
            display: flex;
            align-items: center;
            justify-content: center;
            width: 24px;
            height: 24px;

            :deep(.material-design-icon__svg) {
                font-size: 24px;
                color: var(--ks-content-secondary);
                vertical-align: middle;
            }
        }

        .usage-label {
            line-height: 1;
            display: flex;
            align-items: center;
            font-size: 14px;
            color: var(--ks-content-primary);
        }

        .usage-divider {
            flex: 1;
            height: 1px;
            border-top: 1px dashed var(--ks-border-primary);
        }

        .usage-value {
            line-height: 1;
            display: flex;
            align-items: center;
        }

        .el-button {
            color: var(--ks-content-primary);
            display: flex;
            align-items: center;
        }
    }
}

:deep(.text-search-variant-icon) {
    color: var(--ks-content-tertiary) !important;
}
</style>