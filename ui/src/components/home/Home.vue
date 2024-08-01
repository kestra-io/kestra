<template>
    <top-nav-bar v-if="!embed" :title="routeInfo.title">
        <template #additional-right v-if="canCreate">
            <ul>
                <li>
                    <router-link :to="{name: 'flows/create'}" data-test-id="dashboard-create-button">
                        <el-button :icon="Plus" type="primary">
                            {{ $t('create') }}
                        </el-button>
                    </router-link>
                </li>
            </ul>
        </template>
    </top-nav-bar>
    <section :class="{'container': !embed}" class="home" v-loading="!dailyReady">
        <div v-if="displayCharts">
            <collapse>
                <el-form-item v-if="!flowId && !namespaceRestricted">
                    <namespace-select
                        :data-type="'flow'"
                        :model-value="selectedNamespace"
                        @update:model-value="onNamespaceSelect"
                    />
                </el-form-item>
                <el-form-item>
                    <el-select
                        :model-value="state"
                        @update:model-value="onStateSelect"
                        clearable
                        filterable
                        multiple
                        :placeholder="$t('state')"
                    >
                        <el-option
                            v-for="item in State.allStates()"
                            :key="item.key"
                            :label="item.key"
                            :value="item.key"
                        />
                    </el-select>
                </el-form-item>
                <el-form-item>
                    <date-filter
                        @update:is-relative="onDateFilterTypeChange"
                        @update:filter-value="updateQuery"
                    />
                </el-form-item>
                <el-form-item>
                    <refresh-button class="float-right" @refresh="load" :can-auto-refresh="canAutoRefresh" />
                </el-form-item>
            </collapse>

            <state-global-chart
                class="big mb-4"
                v-if="dailyReady"
                :ready="dailyReady"
                :data="daily"
                :big="true"
                :start-date="startDate"
                :end-date="endDate"
            />

            <home-description v-if="namespace" :description="description" class="mb-4" />

            <el-row v-if="displayPieChart" :gutter="15" class="auto-height mb-4">
                <el-col :lg="8" class="mb-3 mb-xl-0">
                    <home-summary-pie
                        v-if="dailyReady"
                        :title="todayTitle"
                        :data="today"
                    />
                </el-col>
                <el-col :lg="8" class="mb-3 mb-xl-0">
                    <home-summary-pie
                        v-if="dailyReady"
                        :title="yesterdayTitle"
                        :data="yesterday"
                    />
                </el-col>
                <el-col :lg="8" class="mb-3 mb-xl-0">
                    <home-summary-pie
                        v-if="dailyReady"
                        :title="lastDaysTitle"
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
                        :flow-id="flowId"
                        :namespace="namespace"
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
        <div v-else-if="dailyReady">
            <el-card class="pb-3 mb-4">
                <el-row justify="center">
                    <span class="new-execution-img" />
                </el-row>
                <el-row justify="center" class="mb-4">
                    <el-col>
                        <h3 class="text-center">
                            {{ $t('homeDashboard.no executions') }}
                        </h3>
                    </el-col>
                </el-row>
                <el-row justify="center">
                    <trigger-flow v-if="flowId" :disabled="!isAllowedTrigger" :flow-id="flowId" :namespace="namespace" />
                    <router-link v-else :to="{name: 'flows/list'}">
                        <el-button size="large" type="primary">
                            {{ $t('execute') }}
                        </el-button>
                    </router-link>
                </el-row>
            </el-card>
            <onboarding-bottom v-if="!flowId" />
        </div>
    </section>
</template>

<script setup>
    import Plus from "vue-material-design-icons/Plus.vue";
    import RefreshButton from "../layout/RefreshButton.vue";
</script>

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
    import TriggerFlow from "../flows/TriggerFlow.vue";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import OnboardingBottom from "../onboarding/OnboardingBottom.vue";
    import TopNavBar from "../layout/TopNavBar.vue";
    import DateFilter from "../executions/date-select/DateFilter.vue";
    import HomeStartup from "override/mixins/homeStartup"
    import State from "../../utils/state";

    export default {
        mixins: [RouteContext, RestoreUrl, HomeStartup],
        components: {
            DateFilter,
            OnboardingBottom,
            Collapse,
            StateGlobalChart,
            NamespaceSelect,
            HomeSummaryPie,
            HomeSummaryFailed,
            HomeSummaryLog,
            HomeSummaryNamespace,
            HomeDescription,
            TriggerFlow,
            TopNavBar
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
            description: {
                type: String,
                default: undefined
            }
        },
        created() {
            // Auth but no permission at all or no permission to load execution stats
            if (this.user && (!this.user.hasAnyRole() || !this.user.hasAnyActionOnAnyNamespace(permission.EXECUTION, action.READ))) {
                this.$router.push({name:"errors/403"});
                return;
            }
            this.load();
        },
        watch: {
            $route(newValue, oldValue) {
                if (oldValue.name === newValue.name && newValue.query !== oldValue.query) {
                    this.loadStats();
                }
            },
            flowId() {
                this.load();
            }
        },
        data() {
            return {
                isDefaultNamespaceAllow: true,
                dailyReady: false,
                dailyGroupByFlowReady: false,
                today: undefined,
                yesterday: undefined,
                executionCounts: undefined,
                alls: undefined,
                namespacesStats: undefined,
                namespaceRestricted: !!this.namespace,
                refreshDates: false,
                canAutoRefresh: false,
                state: []
            };
        },
        methods: {
            onDateFilterTypeChange(event) {
                this.canAutoRefresh = event;
            },
            loadQuery(base, stats) {
                let queryFilter = _cloneDeep(this.$route.query);

                if (stats) {
                    delete queryFilter["startDate"];
                    delete queryFilter["endDate"];
                    delete queryFilter["timeRange"];
                }

                if (this.selectedNamespace) {
                    queryFilter["namespace"] = this.selectedNamespace;
                }

                if (this.flowId) {
                    queryFilter["flowId"] = this.flowId;
                }

                return _merge(base, queryFilter)
            },
            load() {
                if (this.user && this.user.hasAnyActionOnAnyNamespace(permission.EXECUTION, action.READ)) {
                    this.loadStats();
                    this.haveExecutions();
                }
            },
            haveExecutions() {
                let params = {
                    size: 1,
                    commit: false
                };
                if (this.selectedNamespace) {
                    params["namespace"] = this.selectedNamespace;
                }

                if (this.flowId) {
                    params["flowId"] = this.flowId;
                }
                this.$store.dispatch("execution/findExecutions", params)
                    .then(executions => {
                        this.executionCounts = executions.total;
                    });
            },
            loadStats() {
                this.refreshDates = !this.refreshDates;
                this.dailyReady = false;
                this.$store
                    .dispatch("stat/daily", this.loadQuery({
                        startDate: this.$moment(this.startDate).toISOString(true),
                        endDate: this.$moment(this.endDate).toISOString(true)
                    }, true))
                    .then((daily) => {
                        let data = [...daily];
                        let sorted = data.sort((a, b) => {
                            return new Date(b.date) - new Date(a.date);
                        });
                        this.today = sorted.at(sorted.length - 1);
                        this.yesterday = sorted.length >= 2 ? sorted.at(sorted.length - 2) : {};
                        this.alls = this.mergeStats(sorted);
                        this.dailyReady = true;
                    });

                if (!this.flowId) {
                    this.dailyGroupByFlowReady = false;
                    this.$store
                        .dispatch("stat/dailyGroupByFlow", this.loadQuery({
                            startDate: this.$moment(this.startDate).toISOString(true),
                            endDate: this.$moment(this.endDate).toISOString(true),
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
                if(namespace !== ""){
                    this.$router.push({
                        query: {...this.$route.query, namespace}
                    });
                } else {
                    let query = _cloneDeep(this.$route.query);
                    delete query["namespace"];
                    this.$router.push({
                        query: {...query}
                    });
                }
            },
            updateQuery(queryParam) {
                let query = {...this.$route.query};
                for (const [key, value] of Object.entries(queryParam)) {
                    if (value === undefined || value === "" || value === null) {
                        delete query[key]
                    } else {
                        query[key] = value;
                    }
                }

                this.$router.push({query: query}).then(this.load);
            },
            onStateSelect(state) {
                this.state = state;
                if (state && state.length > 0) {
                    this.$router.push({query: {...this.$route.query, state: state}});
                } else {
                    let query = {...this.$route.query}
                    delete query["state"]
                    this.$router.push({query: query});
                }

                this.load(this.onDataLoaded);
            },
        },
        computed: {
            ...mapState("stat", ["daily", "dailyGroupByFlow"]),
            ...mapState("auth", ["user"]),
            routeInfo() {
                return {
                    title: this.$t("homeDashboard.title"),
                };
            },
            canCreate() {
                return this.user.isAllowedGlobal(permission.FLOW, action.CREATE)
            },
            defaultFilters() {
                return {
                    startDate: this.$moment(this.startDate).toISOString(true),
                    endDate: this.$moment(this.endDate).toISOString(true)
                }
            },
            displayCharts() {
                if (this.executionCounts > 0) {
                    return true
                }
                // not flow home
                if (!this.flowId) {
                    if (!this.$route.query.namespace && this.executionCounts === 0) {
                        return false;
                    }
                }
                if (this.executionCounts === 0) {
                    return false;
                }

                return true;
            },
            selectedNamespace() {
                return this.namespace || this.$route.query.namespace;
            },
            endDate() {
                if (this.$route.query.endDate) {
                    return this.$route.query.endDate;
                }
                return undefined;
            },
            startDate() {
                // This allow to force refresh this computed property especially when using timeRange
                this.refreshDates;
                if (this.$route.query.startDate) {
                    return this.$route.query.startDate;
                }
                if (this.$route.query.timeRange) {
                    return this.$moment().subtract(this.$moment.duration(this.$route.query.timeRange).as("milliseconds")).toISOString(true);
                }

                // the default is PT30D
                return this.$moment().subtract(30, "days").toISOString(true);
            },
            isAllowedTrigger() {
                return this.user && this.user.isAllowed(permission.EXECUTION, action.CREATE, this.namespace);
            },
            todayTitle() {
                return this.$t("homeDashboard.today");
            },
            yesterdayTitle() {
                return this.$t("homeDashboard.yesterday");
            },
            lastDaysTitle() {
                return this.$t("homeDashboard.lastXdays",
                               {days: this.$moment(this.yesterday.date)
                                   .diff(this.$moment(this.startDate), "days")});
            },
            displayPieChart() {
                return this.$moment(this.endDate).isSame(this.$moment(), "day")
                    && this.$moment(this.today?.date).isSame(this.$moment(), "day")
                    && this.$moment(this.endDate).diff(this.$moment(this.startDate), "days") >= 3;
            }
        }
    };
</script>

<style lang="scss" scoped>
    @import "@kestra-io/ui-libs/src/scss/variables";

    .home {
        .auto-height {
            .el-card {
                height: 100%;
            }
        }

        .new-execution-img {
            height: 300px;
            width: 100%;
            background: url("../../assets/home/execute-light.svg") no-repeat center;

            html.dark & {
                background: url("../../assets/home/execute-dark.svg") no-repeat center;
            }
        }
    }
</style>
