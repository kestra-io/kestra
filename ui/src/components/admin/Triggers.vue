<template>
    <top-nav-bar :title="routeInfo.title" />
    <section data-component="Triggers" class="container" v-if="ready">
        <div>
            <data-table
                @page-changed="onPageChanged"
                ref="dataTable"
                :total="total"
            >
                <template #navbar>
                    <div style="display: flex; align-items: center; gap: 12px;">
                        <KestraFilter
                            prefix="triggers"
                            :include="['namespace', 'trigger_state']"
                            :buttons="{ settings: {shown: false} }"
                        />
                        <el-button
                            class="backfill-button"
                            type="primary"
                            style="background-color: #8833FF; border-color: #8833FF; color: #fff;"
                            @click="setBackfillModal(null, true)"
                        >
                            <CalendarCollapseHorizontalOutline class="icon" />
                            <span>Backfill executions</span>
                        </el-button>
                    </div>
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
                                <el-button @click="setDisabledTriggers(false)" type="primary" size="small">
                                    {{ $t("enable") }}
                                </el-button>
                                <el-button @click="setDisabledTriggers(true)" type="primary" size="small">
                                    {{ $t("disable") }}
                                </el-button>
                                <el-button @click="unlockTriggers()" type="primary" size="small">
                                    {{ $t("unlock") }}
                                </el-button>
                                <el-button @click="pauseBackfills()" type="primary" size="small">
                                    <CalendarCollapseHorizontalOutline class="icon" />
                                    {{ $t("pause backfills") }}
                                </el-button>
                                <el-button @click="unpauseBackfills()" type="primary" size="small">
                                    <CalendarCollapseHorizontalOutline class="icon" />
                                    {{ $t("continue backfills") }}
                                </el-button>
                                <el-button @click="deleteBackfills()" type="primary" size="small">
                                    <CalendarCollapseHorizontalOutline class="icon" />
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
                        <!-- Backfill column -->
                        <el-table-column :label="'Backfill'" :width="200" prop="backfillText">
                            <template #default="scope">
                                <button
                                    style="background-color: #8833FF; border: 1px solid #8833FF; color: #fff; margin-top: 8px; padding: 8px 16px; border-radius: 4px; display: flex; align-items: center; cursor: pointer;"
                                    @click="setBackfillModal(scope.row, true)"
                                >
                                    Backfill executions
                                </button>
                            </template>
                        </el-table-column>
                        <!-- Move "Restart the trigger" button to "Actions" column -->
                        <el-table-column
                            v-if="user.hasAnyAction(permission.EXECUTION, action.UPDATE)"
                            :label="$t('actions')"
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
                        <el-table-column :label="$t('actions')" column-key="disable" class-name="row-action">
                            <template #default="scope">
                                <el-switch
                                    v-if="!scope.row.missingSource"
                                    :active-text="$t('enabled')"
                                    :model-value="!scope.row.disabled"
                                    @change="setDisabled(scope.row, $event)"
                                    class="switch-text"
                                    :active-action-icon="Check"
                                />
                                <el-tooltip v-else :content="'flow source not found'" effect="light">
                                    <AlertCircle class="trigger-issue-icon" />
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

            <el-dialog v-model="isBackfillOpen" destroy-on-close :append-to-body="true">
                <template #header>
                    <span>Backfill executions</span>
                </template>
                <el-form :model="backfill" label-position="top">
                    <div class="pickers">
                        <div class="small-picker">
                            <el-form-item label="Start">
                                <el-date-picker
                                    v-model="backfill.start"
                                    type="datetime"
                                    placeholder="Start"
                                />
                            </el-form-item>
                        </div>
                        <div class="small-picker">
                            <el-form-item label="End">
                                <el-date-picker
                                    v-model="backfill.end"
                                    type="datetime"
                                    placeholder="End"
                                />
                            </el-form-item>
                        </div>
                    </div>
                </el-form>
                <template #footer>
                    <el-button type="primary" @click="postBackfill()">
                        Execute backfill
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
    import Calendar from "vue-material-design-icons/Calendar.vue";
    import { CalendarCollapseHorizontalOutline } from 'vue-material-design-icons';
    import Kicon from "../Kicon.vue";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import TopNavBar from "../layout/TopNavBar.vue";
    import Check from "vue-material-design-icons/Check.vue";
    import AlertCircle from "vue-material-design-icons/AlertCircle.vue";
    import SelectTable from "../layout/SelectTable.vue";
    import BulkSelect from "../layout/SelectTable.vue";
    import Cron from "../layout/Cron.vue";
    import TriggerAvatar from "../flows/TriggerAvatar.vue";
</script>

<script>
    import RouteContext from "../../mixins/routeContext";
    import RestoreUrl from "../../mixins/restoreUrl";
    import DataTable from "../layout/DataTable.vue";
    import DataTableActions from "../../mixins/dataTableActions";
    import MarkdownTooltip from "../layout/MarkdownTooltip.vue";
    import DateAgo from "../layout/DateAgo.vue";
    import Id from "../Id.vue";
    import { mapState } from "vuex";
    import SelectTableActions from "../../mixins/selectTableActions";
    import _merge from "lodash/merge";
    import LogsWrapper from "../logs/LogsWrapper.vue";
    import KestraFilter from "../filter/KestraFilter.vue";

    export default {
        name: 'Triggers',
        mixins: [RouteContext, RestoreUrl, DataTableActions, SelectTableActions],
        components: {
            KestraFilter,
            MarkdownTooltip,
            DataTable,
            DateAgo,
            Id,
            LogsWrapper,
            CalendarCollapseHorizontalOutline,
            LockOff,
            PlayBox,
            PauseBox,
            Kicon,
            Check,
            AlertCircle,
            SelectTable,
            BulkSelect,
            Cron,
            TriggerAvatar
        },
        data() {
            return {
                triggers: [],
                total: 0,
                triggerToUnlock: undefined,
                state: undefined,
                states: [
                    {label: this.$t("triggers_state.options.enabled"), value: "ENABLED"},
                    {label: this.$t("triggers_state.options.disabled"), value: "DISABLED"}
                ],
                selection: null,
                isBackfillOpen: false,
                selectedTrigger: null,
                backfill: {
                    start: null,
                    end: null
                },
                ready: true,
            };
        },
        computed: {
            ...mapState("auth", ["user"]),
            routeInfo() {
                return {
                    title: this.$t("triggers")
                };
            },
            triggersMerged() {
                return this.triggers.map(t => ({
                    ...t?.abstractTrigger,
                    ...t?.triggerContext,
                    id: t?.id,
                    type: t?.type || t?.abstractTrigger?.type,
                    codeDisabled: t?.codeDisabled || false,
                    disabled: t?.disabled || false,
                    missingSource: t?.missingSource || false,
                    backfillText: 'STATIC TEST'
                }));
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
        },
        mounted() {
            this.loadData();
        },
        methods: {
            hasLogsContent(row) {
                return row.logs && row.logs.length > 0;
            },
            getClasses(row) {
                return this.hasLogsContent(row) ? "expandable" : "no-expand";
            },
            onSelectionChange(selection) {
                this.selection = selection;
            },
            loadData(callback) {
                console.log('Loading triggers data...');
                const query = this.loadQuery({
                    size: parseInt(this.$route.query.size || 25),
                    page: parseInt(this.$route.query.page || 1),
                    sort: this.$route.query.sort || "triggerId:asc"
                });
                
                this.$store.dispatch("trigger/search", {
                    ...query,
                    namespace: this.$route.query.namespace,
                    q: this.$route.query.q
                }).then(triggersData => {
                    console.log('Raw triggers data:', triggersData);
                    console.log('Trigger results:', triggersData.results);
                    this.triggers = triggersData.results;
                    this.total = triggersData.total;
                    if (callback) {
                        callback();
                    }
                }).catch(error => {
                    console.error('Error loading triggers:', error);
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
                    });
            },
            genericConfirmAction(toast, queryAction, byIdAction, success, data) {
                this.$toast().confirm(
                    this.$t(toast, {"count": this.queryBulkAction ? this.total : this.selection.length}),
                    () => this.genericConfirmCallback(queryAction, byIdAction, success, data),
                    () => {}
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
                            this.loadData();
                        });
                } else {
                    const selection = this.selection;
                    const options = {triggers: selection, ...data};
                    return this.$store
                        .dispatch(byIdAction, byIdAction.includes("setDisabled") ? options : selection)
                        .then(data => {
                            this.$toast().success(this.$t(success, {count: data.count}));
                            this.loadData();
                        }).catch(e => {
                            this.$toast().error(e?.invalids.map(exec => {
                                return {message: this.$t(exec.message, {triggers: exec.invalidValue})};
                            }), this.$t(e.message));
                        });
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
                return _merge(base, queryFilter);
            },
            setBackfillModal(trigger, show) {
                console.log('Opening backfill modal for trigger:', trigger);
                this.selectedTrigger = trigger;
                this.isBackfillOpen = show;
                if (!show) {
                    this.backfill = { start: null, end: null };
                }
            },
            postBackfill() {
                if (this.selectedTrigger && this.backfill.start && this.backfill.end) {
                    console.log('Executing backfill:', {
                        trigger: this.selectedTrigger,
                        backfill: this.backfill
                    });
                    
                    this.$store.dispatch("trigger/backfill", {
                        namespace: this.selectedTrigger.namespace,
                        flowId: this.selectedTrigger.flowId,
                        triggerId: this.selectedTrigger.id,
                        start: this.backfill.start,
                        end: this.backfill.end
                    }).then(() => {
                        this.$message({
                            message: this.$t("backfill.success"),
                            type: "success"
                        });
                        this.loadData();
                    }).catch(error => {
                        this.$message({
                            message: error.message || this.$t("backfill.error"),
                            type: "error"
                        });
                    });
                    
                    this.isBackfillOpen = false;
                    this.backfill = { start: null, end: null };
                }
            },
            isScheduleType(trigger) {
                console.log('Checking trigger:', trigger);
                if (trigger && trigger.cron) {
                    console.log('Found cron property:', trigger.cron);
                    return true;
                }
                const type = trigger?.type || trigger?.abstractTrigger?.type;
                if (!type) {
                    console.log('No type found in trigger');
                    return false;
                }
                console.log('Checking type:', type);
                const scheduleTypes = [
                    "daily",
                    "schedule",
                    "Schedule",
                    "io.kestra.core.models.triggers.types.Schedule",
                    "io.kestra.plugin.core.triggers.Schedule"
                ];
                const isScheduleType = scheduleTypes.some(scheduleType => 
                    type.toLowerCase().includes(scheduleType.toLowerCase())
                );
                console.log('Is schedule type:', isScheduleType);
                return isScheduleType;
            }
        }
    };
</script>

<style>
    .data-table-wrapper {
        margin-left: 0 !important;
        padding-left: 0 !important;
    }
    .backfillContainer {
        display: flex;
        align-items: center;
        gap: 8px;
        min-height: 32px;
        padding: 0 4px;
    }
    .statusIcon {
        font-size: large;
        display: flex;
        align-items: center;
    }
    .trigger-issue-icon {
        color: var(--ks-content-warning);
        font-size: 1.4em;
    }
    .el-table__expanded-cell[class*=cell] {
        padding: 0;
    }
    .no-expand .el-icon {
        display: none;
    }
    .no-expand .el-table__expand-icon {
        pointer-events: none;
    }
    .pickers {
        display: flex;
        gap: 16px;
    }
    .small-picker {
        flex: 1;
    }
    .backfill-button {
        display: inline-flex !important;
        align-items: center !important;
        gap: 8px !important;
        background-color: #8833FF !important;
        border-color: #8833FF !important;
        padding: 8px 16px !important;
        font-size: 14px !important;
        height: 32px !important;
    }
    .backfill-button .icon {
        width: 18px;
        height: 18px;
        margin-right: 4px;
    }
    .backfill-button:hover {
        background-color: #7029D9 !important;
        border-color: #7029D9 !important;
    }
    .backfill-button span {
        line-height: 1;
    }
    .d-flex {
        display: flex;
    }
    .align-items-center {
        align-items: center;
    }
    /* Add consistent styling for all backfill-related buttons */
    .el-button .icon {
        width: 18px;
        height: 18px;
        margin-right: 4px;
    }
    .el-button[type="primary"] {
        display: inline-flex !important;
        align-items: center !important;
        gap: 8px !important;
    }
</style>