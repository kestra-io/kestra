<template>
    <top-nav-bar :title="routeInfo.title" />
    <section data-component="FILENAME_PLACEHOLDER" class="container" v-if="ready">
        <div>
            <data-table
                @page-changed="onPageChanged"
                ref="dataTable"
                :total="total"
            >
                <template #navbar>
                    <KestraFilter
                        prefix="triggers"
                        :domain="TriggerFilterLanguage.domain"
                        :buttons="{
                            refresh: {shown: true, callback: () => load()},
                            settings: {shown: false}
                        }"
                    />
                </template>
                <template #table>
                    <select-table
                        :data="triggersMerged"
                        ref="selectTable"
                        :default-sort="{prop: 'flowId', order: 'ascending'}"
                        table-layout="auto"
                        fixed
                        @sort-change="onSort"
                        @selection-change="onSelectionChange"
                        expandable
                        :row-class-name="getClasses"
                        :no-data-text="$t('no_results.triggers')"
                    >
                        <template #expand>
                            <el-table-column type="expand">
                                <template #default="props">
                                    <LogsWrapper class="m-3" :filters="props.row" v-if="hasLogsContent(props.row)" :charts="false" embed />
                                </template>
                            </el-table-column>
                        </template>
                        <template #select-actions>
                            <bulk-select
                                :select-all="queryBulkAction"
                                :selections="selection"
                                :total="total"
                                @update:select-all="toggleAllSelection"
                                @unselect="toggleAllUnselected"
                            >
                                <el-button @click="setDisabledTriggers(false)">
                                    {{ $t("enable") }}
                                </el-button>
                                <el-button @click="setDisabledTriggers(true)">
                                    {{ $t("disable") }}
                                </el-button>
                                <el-button @click="unlockTriggers()">
                                    {{ $t("unlock") }}
                                </el-button>
                                <el-button @click="pauseBackfills()">
                                    {{ $t("pause backfills") }}
                                </el-button>
                                <el-button @click="unpauseBackfills()">
                                    {{ $t("continue backfills") }}
                                </el-button>
                                <el-button @click="deleteBackfills()">
                                    {{ $t("delete backfills") }}
                                </el-button>
                            </bulk-select>
                        </template>
                        <el-table-column
                            v-if="visibleColumns.triggerId"
                            prop="triggerId"
                            sortable="custom"
                            :sort-orders="['ascending', 'descending']"
                            :label="$t('id')"
                        >
                            <template #default="scope">
                                <div class="text-nowrap">
                                    {{ scope.row.id }}
                                </div>
                            </template>
                        </el-table-column>
                        <el-table-column
                            v-if="visibleColumns.flowId"
                            prop="flowId"
                            sortable="custom"
                            :sort-orders="['ascending', 'descending']"
                            :label="$t('flow')"
                        >
                            <template #default="scope">
                                <router-link
                                    :to="{name: 'flows/update', params: {namespace: scope.row.namespace, id: scope.row.flowId}}"
                                >
                                    {{ $filters.invisibleSpace(scope.row.flowId) }}
                                </router-link>
                                <markdown-tooltip
                                    :id="scope.row.namespace + '-' + scope.row.flowId"
                                    :description="scope.row.description"
                                    :title="scope.row.namespace + '.' + scope.row.flowId"
                                />
                            </template>
                        </el-table-column>
                        <el-table-column
                            v-if="visibleColumns.namespace"
                            prop="namespace"
                            sortable="custom"
                            :sort-orders="['ascending', 'descending']"
                            :label="$t('namespace')"
                        >
                            <template #default="scope">
                                {{ $filters.invisibleSpace(scope.row.namespace) }}
                            </template>
                        </el-table-column>

                        <el-table-column v-if="visibleColumns.executionId" :label="$t('current execution')">
                            <template #default="scope">
                                <router-link
                                    v-if="scope.row.executionId"
                                    :to="{name: 'executions/update', params: {namespace: scope.row.namespace, flowId: scope.row.flowId, id: scope.row.executionId}}"
                                >
                                    <id :value="scope.row.executionId" :shrink="true" />
                                </router-link>
                            </template>
                        </el-table-column>
                        <el-table-column v-if="visibleColumns.workerId" prop="workerId" :label="$t('workerId')">
                            <template #default="scope">
                                <id
                                    :value="scope.row.workerId"
                                    :shrink="true"
                                />
                            </template>
                        </el-table-column>
                        <el-table-column v-if="visibleColumns.date" :label="$t('date')">
                            <template #default="scope">
                                <date-ago :inverted="true" :date="scope.row.date" />
                            </template>
                        </el-table-column>
                        <el-table-column v-if="visibleColumns.updatedDate" :label="$t('updated date')">
                            <template #default="scope">
                                <date-ago :inverted="true" :date="scope.row.updatedDate" />
                            </template>
                        </el-table-column>
                        <el-table-column
                            v-if="visibleColumns.nextExecutionDate"
                            prop="nextExecutionDate"
                            sortable="custom"
                            :sort-orders="['ascending', 'descending']"
                            :label="$t('next execution date')"
                        >
                            <template #default="scope">
                                <date-ago :inverted="true" :date="scope.row.nextExecutionDate" />
                            </template>
                        </el-table-column>
                        <el-table-column :label="$t('cron')">
                            <template #default="scope">
                                <Cron v-if="scope.row.cron" :cron-expression="scope.row?.cron" />
                            </template>
                        </el-table-column>
                        <el-table-column :label="$t('details')">
                            <template #default="scope">
                                <TriggerAvatar
                                    :flow="{flowId: scope.row.flowId, namespace: scope.row.namespace, triggers: [scope.row]}"
                                    :trigger-id="scope.row.id"
                                />
                            </template>
                        </el-table-column>
                        <el-table-column v-if="visibleColumns.evaluateRunningDate" :label="$t('evaluation lock date')">
                            <template #default="scope">
                                <date-ago :inverted="true" :date="scope.row.evaluateRunningDate" />
                            </template>
                        </el-table-column>
                        <el-table-column
                            v-if="user.hasAnyAction(permission.EXECUTION, action.UPDATE)"
                            column-key="action"
                            class-name="row-action"
                        >
                            <template #default="scope">
                                <el-button v-if="scope.row.executionId || scope.row.evaluateRunningDate">
                                    <kicon
                                        :tooltip="$t(`unlock trigger.tooltip.${scope.row.executionId ? 'execution' : 'evaluation'}`)"
                                        placement="left"
                                        @click="triggerToUnlock = scope.row"
                                    >
                                        <lock-off />
                                    </kicon>
                                </el-button>
                            </template>
                        </el-table-column>
                        <el-table-column :label="$t('backfill')" column-key="backfill">
                            <template #default="scope">
                                <div class="backfillContainer items-center gap-2">
                                    <span v-if="scope.row.backfill" class="statusIcon">
                                        <el-tooltip v-if="!scope.row.backfill.paused" :content="$t('backfill running')" effect="light">
                                            <play-box font />
                                        </el-tooltip>
                                        <el-tooltip v-else :content="$t('backfill paused')">
                                            <pause-box />
                                        </el-tooltip>
                                    </span>

                                    <el-button
                                        :icon="CalendarCollapseHorizontalOutline"
                                        v-if="user.hasAnyAction(permission.EXECUTION, action.UPDATE)"
                                        @click="restart(scope.row)"
                                        size="small"
                                        type="primary"
                                    >
                                        {{ $t("backfill executions") }}
                                    </el-button>
                                </div>
                            </template>
                        </el-table-column>


                        <el-table-column :label="$t('actions')" column-key="disable" class-name="row-action">
                            <template #default="scope">
                                <el-tooltip
                                    v-if="!scope.row.missingSource"
                                    :content="$t('trigger disabled')"
                                    :disabled="!scope.row.codeDisabled"
                                    effect="light"
                                >
                                    <el-switch
                                        :active-text="$t('enabled')"
                                        :inactive-text="$t('disabled')"
                                        :model-value="!(scope.row.disabled || scope.row.codeDisabled)"
                                        @change="setDisabled(scope.row, $event)"
                                        inline-prompt
                                        class="switch-text"
                                        :disabled="scope.row.codeDisabled"
                                    />
                                </el-tooltip>
                                <el-tooltip v-else :content="$t('flow source not found')" effect="light">
                                    <AlertCircle />
                                </el-tooltip>
                            </template>
                        </el-table-column>
                    </select-table>
                </template>
            </data-table>

            <el-dialog v-model="triggerToUnlock" destroy-on-close :append-to-body="true">
                <template #header>
                    <span v-html="$t('unlock trigger.confirmation')" />
                </template>
                {{ $t("unlock trigger.warning") }}
                <template #footer>
                    <el-button :icon="LockOff" @click="unlock" type="primary">
                        {{ $t("unlock trigger.button") }}
                    </el-button>
                </template>
            </el-dialog>
        </div>
    </section>
</template>
<script setup>
    import LockOff from "vue-material-design-icons/LockOff.vue";
    import PlayBox from "vue-material-design-icons/PlayBox.vue";
    import PauseBox from "vue-material-design-icons/PauseBox.vue";
    import Kicon from "../Kicon.vue";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import TopNavBar from "../layout/TopNavBar.vue";
    import AlertCircle from "vue-material-design-icons/AlertCircle.vue";
    import SelectTable from "../layout/SelectTable.vue";
    import BulkSelect from "../layout/BulkSelect.vue";
    import Cron from "../layout/Cron.vue";
    import TriggerAvatar from "../flows/TriggerAvatar.vue";
    import CalendarCollapseHorizontalOutline from "vue-material-design-icons/CalendarCollapseHorizontalOutline.vue";
    import TriggerFilterLanguage from "../../composables/monaco/languages/filters/impl/triggerFilterLanguage.ts";
</script>
<script>
    import RouteContext from "../../mixins/routeContext";
    import RestoreUrl from "../../mixins/restoreUrl";
    import DataTable from "../layout/DataTable.vue";
    import DataTableActions from "../../mixins/dataTableActions";
    import MarkdownTooltip from "../layout/MarkdownTooltip.vue";
    import DateAgo from "../layout/DateAgo.vue";
    import Id from "../Id.vue";
    import {mapState} from "vuex";
    import SelectTableActions from "../../mixins/selectTableActions";
    import _merge from "lodash/merge";
    import LogsWrapper from "../logs/LogsWrapper.vue";
    import KestraFilter from "../filter/KestraFilter.vue"

    export default {
        mixins: [RouteContext, RestoreUrl, DataTableActions, SelectTableActions],
        components: {
            KestraFilter,
            MarkdownTooltip,
            DataTable,
            DateAgo,
            Id,
            LogsWrapper
        },
        data() {
            return {
                triggers: undefined,
                total: undefined,
                triggerToUnlock: undefined,
                states: [
                    {label: this.$t("triggers_state.options.enabled"), value: "ENABLED"},
                    {label: this.$t("triggers_state.options.disabled"), value: "DISABLED"}
                ],
                selection: null
            };
        },
        methods: {
            hasLogsContent(row) {
                return row.logs && row.logs.length > 0;
            },
            getClasses(row) {
                return this.hasLogsContent(row) ? "expandable" : "no-expand"; // Return class based on logs
            },
            onSelectionChange(selection) {
                this.selection = selection;
            },
            loadData(callback) {
                const query = this.loadQuery({
                    size: parseInt(this.$route.query.size || 25),
                    page: parseInt(this.$route.query.page || 1),
                    sort: this.$route.query.sort || "triggerId:asc"
                });

                for (const key in query) {
                    if (key.startsWith("filters[trigger_state]")) {
                        delete query[key];
                    }
                }

                this.$store.dispatch("trigger/search", query).then(triggersData => {
                    this.triggers = triggersData.results;
                    this.total = triggersData.total;
                    if (callback) {
                        callback();
                    }
                });
            },
            async unlock() {
                const namespace = this.triggerToUnlock.namespace;
                const flowId = this.triggerToUnlock.flowId;
                const triggerId = this.triggerToUnlock.triggerId;
                const unlockedTrigger = await this.$store.dispatch("trigger/unlock", {
                    namespace: namespace,
                    flowId: flowId,
                    triggerId: triggerId
                });

                this.$message({
                    message: this.$t("unlock trigger.success"),
                    type: "success"
                });

                const triggerIdx = this.triggers.findIndex(trigger => trigger.namespace === namespace && trigger.flowId === flowId && trigger.triggerId === triggerId);
                if (triggerIdx !== -1) {
                    this.triggers[triggerIdx] = unlockedTrigger;
                }

                this.triggerToUnlock = undefined;
            },
            restart(trigger) {
                this.$store.dispatch("trigger/restart", {
                    namespace: trigger.namespace,
                    flowId: trigger.flowId,
                    triggerId: trigger.triggerId
                }).then(newTriggerContext => {
                    this.$toast().saved(newTriggerContext.id);
                    this.triggers = this.triggers.map(t => {
                        if (t.id === newTriggerContext.id) {
                            let triggerCopy = t;
                            triggerCopy.triggerContext = newTriggerContext;
                            return triggerCopy;
                        }
                        return t
                    })
                })
            },
            setDisabled(trigger, value) {
                if (trigger.codeDisabled) {
                    this.$message({
                        message: this.$t("triggerflow disabled"),
                        type: "error",
                        showClose: true,
                        duration: 1500
                    });
                    return;
                }
                this.$store.dispatch("trigger/update", {...trigger, disabled: !value})
                    .then(_ => {
                        this.loadData();
                    })
            },
            genericConfirmAction(toast, queryAction, byIdAction, success, data) {
                this.$toast().confirm(
                    this.$t(toast, {"count": this.queryBulkAction ? this.total : this.selection.length}),
                    () => this.genericConfirmCallback(queryAction, byIdAction, success, data),
                    () => {
                    }
                );
            },
            genericConfirmCallback(queryAction, byIdAction, success, data) {
                if (this.queryBulkAction) {
                    const query = this.loadQuery({});
                    const options = {...query, ...data};
                    return this.$store
                        .dispatch(queryAction, options)
                        .then(data => {
                            this.$toast().success(this.$t(success, {count: data.count}));
                            this.loadData()
                        })
                } else {
                    const selection = this.selection;
                    const options = {triggers: selection, ...data};
                    return this.$store
                        .dispatch(byIdAction, byIdAction.includes("setDisabled") ? options : selection)
                        .then(data => {
                            this.$toast().success(this.$t(success, {count: data.count}));
                            this.loadData()
                        }).catch(e => {
                            this.$toast().error(e?.invalids.map(exec => {
                                return {message: this.$t(exec.message, {triggers: exec.invalidValue})}
                            }), this.$t(e.message))
                        })
                }
            },
            unpauseBackfills() {
                this.genericConfirmAction(
                    "bulk unpause backfills",
                    "trigger/unpauseBackfillByQuery",
                    "trigger/unpauseBackfillByTriggers",
                    "bulk success unpause backfills"
                );
            },
            pauseBackfills() {
                this.genericConfirmAction(
                    "bulk pause backfills",
                    "trigger/pauseBackfillByQuery",
                    "trigger/pauseBackfillByTriggers",
                    "bulk success pause backfills"
                );
            },
            deleteBackfills() {
                this.genericConfirmAction(
                    "bulk delete backfills",
                    "trigger/deleteBackfillByQuery",
                    "trigger/deleteBackfillByTriggers",
                    "bulk success delete backfills"
                );
            },
            unlockTriggers() {
                this.genericConfirmAction(
                    "bulk unlock",
                    "trigger/unlockByQuery",
                    "trigger/unlockByTriggers",
                    "bulk success unlock"
                );
            },
            setDisabledTriggers(bool) {
                this.genericConfirmAction(
                    `bulk disabled status.${bool}`,
                    "trigger/setDisabledByQuery",
                    "trigger/setDisabledByTriggers",
                    `bulk success disabled status.${bool}`,
                    {disabled: bool}
                );
            },
            loadQuery(base) {
                let queryFilter = this.queryWithFilter();

                return _merge(base, queryFilter)
            },
        },
        computed: {
            ...mapState("auth", ["user"]),
            routeInfo() {
                return {
                    title: this.$t("triggers")
                }
            },
            triggersMerged() {
                const all = this.triggers.map(t => {
                    return {
                        ...t?.abstractTrigger,
                        ...t.triggerContext,
                        codeDisabled: t?.abstractTrigger?.disabled,
                        // if we have no abstract trigger, it means that flow or trigger definition hasn't been found
                        missingSource: !t.abstractTrigger
                    }
                })

                if(!this.$route.query?.["filters[trigger_state][EQUALS]"]?.length) return all;

                const disabled = this.$route.query?.["filters[trigger_state][EQUALS]"] === "DISABLED" ? true : false;
                return all.filter(trigger => trigger.disabled === disabled);
            },
            visibleColumns() {
                const columns = [
                    {prop: "triggerId", label: this.$t("id")},
                    {prop: "flowId", label: this.$t("flow")},
                    {prop: "namespace", label: this.$t("namespace")},
                    {prop: "executionId", label: this.$t("current execution")},
                    {prop: "executionCurrentState", label: this.$t("state")},
                    {prop: "workerId", label: this.$t("workerId")},
                    {prop: "date", label: this.$t("date")},
                    {prop: "updatedDate", label: this.$t("updated date")},
                    {prop: "nextExecutionDate", label: this.$t("next execution date")},
                    {prop: "evaluateRunningDate", label: this.$t("evaluation lock date")},
                ];

                return columns.reduce((acc, column) => {
                    acc[column.prop] = this.triggersMerged.some(trigger => trigger[column.prop]);
                    return acc;
                }, {});
            }
        }
    };
</script>
<style lang="scss" scoped>
    .data-table-wrapper {
        margin-left: 0 !important;
        padding-left: 0 !important;
    }

    .backfillContainer{
        display: flex;
        align-items: center;
    }
    .statusIcon{
        font-size: large;
    }

    .trigger-issue-icon {
        color: var(--ks-content-warning);
        font-size: 1.4em;
    }

    .alert-circle-icon {
        color: var(--ks-content-warning);
        font-size: 1.4em;
    }

    :deep(.el-table__expand-icon) {
        pointer-events: none;
        .el-icon {
            display: none;
        }
    }
    :deep(.el-switch) {
        .is-text {
            padding: 0 3px;
            color: inherit;
        }

        &.is-checked {
            .is-text {
                color: #ffffff;
            }
        }
    }
</style>