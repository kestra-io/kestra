<template>
    <div class="home">
        <div>
            <collapse v-if="!flowId">
                <el-form-item>
                    <namespace-select
                        :data-type="'flow'"
                        :model-value="selectedNamespace"
                        @update:model-value="onNamespaceSelect"
                    />
                </el-form-item>
            </collapse>

            <state-global-chart
                class="big mb-4"
                v-if="dailyReady"
                :ready="dailyReady"
                :data="daily"
                :big="true"
            />

            <home-description v-if="namespace" class="mb-4" />

            <el-row :gutter="15" class="auto-height mb-4">
                <el-col :lg="8" class="mb-3 mb-xl-0">
                    <home-summary-pie
                        v-if="dailyReady"
                        :title="$t('homeDashboard.today')"
                        :data="today"
                    />
                </el-col>
                <el-col :lg="8" class="mb-3 mb-xl-0">
                    <home-summary-pie
                        v-if="dailyReady"
                        :title="$t('homeDashboard.yesterday')"
                        :data="yesterday"
                    />
                </el-col>
                <el-col :lg="8" class="mb-3 mb-xl-0">
                    <home-summary-pie
                        v-if="dailyReady"
                        :title="$t('homeDashboard.last28Days')"
                        :data="alls"
                    />
                </el-col>
            </el-row>

            <el-row :gutter="15" v-if="!namespace" class="auto-height mb-4">
                <el-col :lg="12">
                    <home-summary-namespace
                        v-if="dailyGroupByFlowReady"
                        :data="namespacesStats"
                    />
                </el-col>
                <el-col :lg="12">
                    <home-summary-namespace
                        v-if="dailyGroupByFlowReady"
                        :failed="true"
                        :data="namespacesStats"
                    />
                </el-col>
            </el-row>

            <el-row :gutter="15">
                <el-col :lg="12">
                    <home-summary-failed
                        v-if="dailyReady"
                        :filters="defaultFilters"
                        class="mb-4"
                    />
                </el-col>
                <el-col :lg="12">
                    <home-summary-log
                        v-if="dailyReady"
                        :filters="defaultFilters"
                        class="mb-4"
                    />
                </el-col>
            </el-row>
        </div>
    </div>
</template>
<script>
    import Collapse from "../layout/Collapse.vue";
    import RouteContext from "../../mixins/routeContext";
    import StateGlobalChart from "../stats/StateGlobalChart.vue";
    import {mapState} from "vuex";
    import _cloneDeep from "lodash/cloneDeep"
    import NamespaceSelect from "../namespace/NamespaceSelect.vue";
    import HomeSummaryPie from "./HomeSummaryPie.vue";
    import HomeSummaryFailed from "./HomeSummaryFailed.vue";
    import HomeSummaryLog from "./HomeSummaryLog.vue";
    import RestoreUrl from "../../mixins/restoreUrl";
    import HomeSummaryNamespace from "./HomeSummaryNamespace.vue";
    import HomeDescription from "./HomeDescription.vue";
    import _merge from "lodash/merge";

    export default {
        mixins: [RouteContext, RestoreUrl],
        components: {
            Collapse,
            StateGlobalChart,
            NamespaceSelect,
            HomeSummaryPie,
            HomeSummaryFailed,
            HomeSummaryLog,
            HomeSummaryNamespace,
            HomeDescription
        },
        props: {
            namespace: {
                type: String,
                default: undefined
            },
            flowId: {
                type: String,
                default: undefined
            },
        },
        created() {
            this.loadStats();
        },
        watch: {
            $route(newValue, oldValue) {
                if (oldValue.name === newValue.name && newValue.query !== oldValue.query) {
                    this.loadStats();
                }
            }
        },
        data() {
            return {
                isDefaultNamespaceAllow: true,
                dailyReady: false,
                dailyGroupByFlowReady: false,
                today: undefined,
                yesterday: undefined,
                alls: undefined,
                namespacesStats: undefined
            };
        },
        methods: {
            loadQuery(base) {
                let queryFilter = this.$route.query;

                if (this.selectedNamespace) {
                    queryFilter["namespace"] = this.selectedNamespace;
                }

                if (this.flowId) {
                    queryFilter["flowId"] = this.flowId;
                }

                return _merge(base, queryFilter)
            },
            loadStats() {
                this.dailyReady = false;
                this.$store
                    .dispatch("stat/daily", this.loadQuery({
                        startDate: this.startDate,
                        endDate: this.endDate
                    }))
                    .then((daily) => {
                        let data = [...daily];
                        let sorted = data.sort((a, b) => {
                            return new Date(b.startDate) - new Date(a.startDate);
                        });
                        this.today = sorted.shift();
                        this.yesterday = sorted.shift();
                        this.alls = this.mergeStats(sorted);
                        this.dailyReady = true;
                    });

                if (!this.flowId) {
                    this.dailyGroupByFlowReady = false;
                    this.$store
                        .dispatch("stat/dailyGroupByFlow", this.loadQuery({
                            startDate: this.startDate,
                            endDate: this.endDate,
                            namespaceOnly: true
                        }))
                        .then((daily) => {
                            this.namespacesStats = daily;
                            this.dailyGroupByFlowReady = true;
                        });
                }
            },
            mergeStats(daily) {
                return daily
                    .reduce((accumulator, value)  => {
                        if (!accumulator) {
                            accumulator = _cloneDeep(value);
                        } else {
                            this.sumAll(value.executionCounts, accumulator.executionCounts);
                            this.sumAll(value.duration, accumulator.duration);
                        }
                        return accumulator;
                    }, null);
            },
            sumAll(object, accumulator) {
                for (const key in object) {
                    accumulator[key] += object[key]
                }
            },
            onNamespaceSelect(namespace) {
                this.$router.push({
                    query: {...this.$route.query, namespace}
                });
            },
        },
        computed: {
            ...mapState("stat", ["daily", "dailyGroupByFlow"]),
            routeInfo() {
                return {
                    title: this.$t("home"),
                };
            },
            defaultFilters() {
                return {
                    start: this.$moment(this.startDate).unix() * 1000,
                    end: this.$moment(this.endDate).unix() * 1000 ,
                }
            },
            selectedNamespace() {
                return this.namespace || this.$route.query.namespace;
            },
            endDate() {
                return this.$moment(new Date()).endOf("day").toISOString(true)
            },
            startDate() {
                return this.$moment(this.endDate)
                    .add(-30, "days")
                    .startOf("day")
                    .toISOString(true)
            }
        }
    };
</script>

<style lang="scss">
    .home {
        .auto-height {
            .el-card {
                height: 100%;
            }
        }
    }
</style>
