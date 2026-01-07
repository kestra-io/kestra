<template>
    <TopNavBar :title="routeInfo.title">
        <template #additional-right>
            <el-button :icon="Download" @click="exportTriggersAsStream()">
                {{ t('export_csv') }}
            </el-button>
        </template>
    </TopNavBar>
    <section class="container" v-if="ready">
        <div>
            <DataTable
                @page-changed="onPageChanged"
                ref="dataTable"
                :total="total"
            >
                <template #navbar>
                    <KSFilter
                        :prefix="'triggers'"
                        :configuration="triggerFilter"
                        @update-properties="updateDisplayColumns"
                        :tableOptions="{
                            chart: {shown: false},
                            refresh: {shown: true, callback: () => load()}
                        }"
                        :properties="{
                            displayColumns,
                            shown: true,
                            columns: optionalColumns,
                            storageKey: storageKey
                        }"
                        :defaultScope="false"
                        :defaultTimeRange="false"
                    />
                </template>
                <template #table>
                    <SelectTable
                        :data="triggersMerged"
                        ref="selectTable"
                        :defaultSort="{prop: 'flowId', order: 'ascending'}"
                        tableLayout="auto"
                        fixed
                        @sort-change="onSort"
                        @selection-change="onSelectionChange"
                        expandable
                        :rowClassName="getClasses"
                        :no-data-text="$t('no_results.triggers')"
                        :rowKey="(row: any) => `${row.namespace}-${row.flowId}-${row.triggerId}`"
                    >
                        <template #expand>
                            <el-table-column type="expand">
                                <template #default="props">
                                    <LogsWrapper
                                        class="m-3"
                                        :filters="props.row"
                                        v-if="hasLogsContent(props.row)"
                                        :withCharts="false"
                                        embed
                                    />
                                </template>
                            </el-table-column>
                        </template>
                        <template #select-actions>
                            <BulkSelect
                                :selectAll="queryBulkAction"
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
                                <el-button @click="deleteTriggers()">
                                    {{ $t("delete triggers") }}
                                </el-button>
                            </BulkSelect>
                        </template>
                        <el-table-column
                            prop="triggerId"
                            sortable="custom"
                            :sortOrders="['ascending', 'descending']"
                            :label="$t('id')"
                        >
                            <template #default="scope">
                                <div class="text-nowrap">
                                    {{ scope.row.id }}
                                </div>
                            </template>
                        </el-table-column>

                        <el-table-column
                            v-for="col in visibleColumns"
                            :key="col.prop"
                            :prop="col.prop"
                            :label="col.label"
                            :sortable="['flowId', 'namespace', 'nextExecutionDate'].includes(col.prop) ? 'custom' : false"
                            :sortOrders="['flowId', 'namespace', 'nextExecutionDate'].includes(col.prop) ? ['ascending', 'descending'] : undefined"
                        >
                            <template #header v-if="col.prop === 'date'">
                                <el-tooltip
                                    :content="$t('last trigger date tooltip')"
                                    placement="top"
                                    effect="light"
                                    popperClass="wide-tooltip"
                                >
                                    <span>{{ col.label }}</span>
                                </el-tooltip>
                            </template>
                            <template #header v-else-if="col.prop === 'updatedDate'">
                                <el-tooltip
                                    :content="$t('context updated date tooltip')"
                                    placement="top"
                                    effect="light"
                                    popperClass="wide-tooltip"
                                >
                                    <span>{{ col.label }}</span>
                                </el-tooltip>
                            </template>
                            <template #header v-else-if="col.prop === 'nextExecutionDate'">
                                <el-tooltip
                                    :content="$t('next evaluation date tooltip')"
                                    placement="top"
                                    effect="light"
                                    popperClass="wide-tooltip"
                                >
                                    <span>{{ col.label }}</span>
                                </el-tooltip>
                            </template>
                            <template #default="scope">
                                <template v-if="col.prop === 'flowId'">
                                    <router-link
                                        v-if="scope.row.namespace && scope.row.flowId"
                                        :to="{name: 'flows/update', params: {namespace: scope.row.namespace, id: scope.row.flowId}}"
                                    >
                                        {{ invisibleSpace(scope.row.flowId) }}
                                    </router-link>
                                    <span v-else>{{ invisibleSpace(scope.row.flowId) }}</span>
                                    <MarkdownTooltip
                                        v-if="scope.row.namespace && scope.row.flowId"
                                        :id="scope.row.namespace + '-' + scope.row.flowId"
                                        :description="scope.row.description"
                                        :title="scope.row.namespace + '.' + scope.row.flowId"
                                    />
                                </template>
                                <template v-else-if="col.prop === 'namespace'">
                                    {{ invisibleSpace(scope.row.namespace) }}
                                </template>
                                <template v-else-if="col.prop === 'executionId'">
                                    <router-link
                                        v-if="scope.row.executionId"
                                        :to="{name: 'executions/update', params: {namespace: scope.row.namespace, flowId: scope.row.flowId, id: scope.row.executionId}}"
                                    >
                                        <Id :value="scope.row.executionId" :shrink="true" />
                                    </router-link>
                                </template>
                                <template v-else-if="col.prop === 'workerId'">
                                    <Id
                                        :value="scope.row.workerId"
                                        :shrink="true"
                                    />
                                </template>
                                <template v-else-if="col.prop === 'date'">
                                    <DateAgo :inverted="true" :date="scope.row.date" />
                                </template>
                                <template v-else-if="col.prop === 'updatedDate'">
                                    <DateAgo :inverted="true" :date="scope.row.updatedDate" />
                                </template>
                                <template v-else-if="col.prop === 'nextExecutionDate'">
                                    <DateAgo :inverted="true" :date="scope.row.nextExecutionDate" />
                                </template>
                                <template v-else-if="col.prop === 'evaluateRunningDate'">
                                    <DateAgo :inverted="true" :date="scope.row.evaluateRunningDate" />
                                </template>
                            </template>
                        </el-table-column>

                        <el-table-column :label="$t('details')">
                            <template #default="scope">
                                <TriggerAvatar
                                    :flow="{id: scope.row.flowId, namespace: scope.row.namespace, triggers: [scope.row]}"
                                    :triggerId="scope.row.id"
                                />
                            </template>
                        </el-table-column>

                        <el-table-column
                            v-if="authStore.user.hasAnyAction(permission.EXECUTION, action.UPDATE)"
                            columnKey="action"
                            className="row-action"
                        >
                            <template #default="scope">
                                <div class="action-container">
                                    <el-button v-if="scope.row.executionId || scope.row.evaluateRunningDate">
                                        <Kicon
                                            :tooltip="$t(`unlock trigger.tooltip.${scope.row.executionId ? 'execution' : 'evaluation'}`)"
                                            placement="left"
                                            @click="triggerToUnlock = scope.row"
                                        >
                                            <LockOff />
                                        </Kicon>
                                    </el-button>
                                    <el-button>
                                        <Kicon
                                            :tooltip="$t('delete trigger')"
                                            placement="left"
                                            @click="confirmDeleteTrigger(scope.row)"
                                        >
                                            <Delete />
                                        </Kicon>
                                    </el-button>
                                </div>
                            </template>
                        </el-table-column>
                        <el-table-column :label="$t('backfill')" columnKey="backfill">
                            <template #default="scope">
                                <div class="backfillContainer items-center gap-2">
                                    <span v-if="scope.row.backfill" class="statusIcon">
                                        <el-tooltip
                                            v-if="!scope.row.backfill.paused"
                                            :content="$t('backfill running')"
                                            effect="light"
                                        >
                                            <PlayBox font />
                                        </el-tooltip>
                                        <el-tooltip v-else :content="$t('backfill paused')">
                                            <PauseBox />
                                        </el-tooltip>
                                    </span>

                                    <el-button
                                        :icon="CalendarCollapseHorizontalOutline"
                                        v-if="authStore.user.hasAnyAction(permission.EXECUTION, action.UPDATE)"
                                        @click="setBackfillModal(scope.row, true)"
                                        size="small"
                                        type="primary"
                                        :disabled="scope.row.disabled || scope.row.codeDisabled"
                                    >
                                        {{ $t("backfill executions") }}
                                    </el-button>
                                </div>
                            </template>
                        </el-table-column>


                        <el-table-column :label="$t('enabled')" columnKey="disable" className="row-action">
                            <template #default="scope">
                                <el-tooltip
                                    v-if="!scope.row.missingSource"
                                    :content="$t('trigger disabled')"
                                    :disabled="!scope.row.codeDisabled"
                                    effect="light"
                                >
                                    <el-switch
                                        :modelValue="!(scope.row.disabled || scope.row.codeDisabled)"
                                        @change="setDisabled(scope.row, $event)"
                                        inlinePrompt
                                        class="switch-text"
                                        :disabled="scope.row.codeDisabled"
                                    />
                                </el-tooltip>
                                <el-tooltip v-else :content="$t('flow source not found')" effect="light">
                                    <AlertCircle />
                                </el-tooltip>
                            </template>
                        </el-table-column>
                    </SelectTable>
                </template>
            </DataTable>

            <el-dialog v-model="triggerToUnlock" destroyOnClose :appendToBody="true">
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

            <el-dialog v-model="isBackfillOpen" destroyOnClose :appendToBody="true">
                <template #header>
                    <span v-html="$t('backfill executions')" />
                </template>
                <el-form :model="backfill" labelPosition="top">
                    <div class="pickers">
                        <div class="small-picker">
                            <el-form-item label="Start">
                                <el-date-picker
                                    v-model="backfill.start"
                                    type="datetime"
                                    placeholder="Start"
                                    :disabledDate="disabledStartDate"
                                />
                            </el-form-item>
                        </div>
                        <div class="small-picker">
                            <el-form-item label="End">
                                <el-date-picker
                                    v-model="backfill.end"
                                    type="datetime"
                                    placeholder="End"
                                    :disabledDate="disabledEndDate"
                                />
                            </el-form-item>
                        </div>
                    </div>
                </el-form>
                <FlowRun
                    @update-inputs="backfill.inputs = $event"
                    @update-labels="backfill.labels = $event"
                    :selectedTrigger="selectedTrigger"
                    :redirect="false"
                    :embed="true"
                />
                <template #footer>
                    <el-button
                        type="primary"
                        @click="postBackfill()"
                        :disabled="checkBackfill"
                    >
                        {{ $t("execute backfill") }}
                    </el-button>
                </template>
            </el-dialog>
        </div>
    </section>
</template>
<script setup lang="ts">
    import _merge from "lodash/merge";
    import {ref, computed, watch} from "vue";
    import moment from "moment";
    import {useI18n} from "vue-i18n";
    import {useRoute} from "vue-router";
    import {ElMessage} from "element-plus";
    import {useToast} from "../../utils/toast";
    import {useFlowStore} from "../../stores/flow";
    import {useAuthStore} from "override/stores/auth";
    import {invisibleSpace} from "../../utils/filters";
    import {storageKeys} from "../../utils/constants";
    import {TriggerDeleteOptions, useTriggerStore} from "../../stores/trigger";
    import {useExecutionsStore} from "../../stores/executions";
    import {useTriggerFilter} from "../filter/configurations";
    import {useDataTableActions} from "../../composables/useDataTableActions";
    import {useSelectTableActions} from "../../composables/useSelectTableActions";
    import {type ColumnConfig, useTableColumns} from "../../composables/useTableColumns";

    import action from "../../models/action";
    import permission from "../../models/permission";
    import LockOff from "vue-material-design-icons/LockOff.vue";
    import PlayBox from "vue-material-design-icons/PlayBox.vue";
    import PauseBox from "vue-material-design-icons/PauseBox.vue";
    import AlertCircle from "vue-material-design-icons/AlertCircle.vue";
    import CalendarCollapseHorizontalOutline from "vue-material-design-icons/CalendarCollapseHorizontalOutline.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import Download from "vue-material-design-icons/Download.vue";

    import Id from "../Id.vue";
    import Kicon from "../Kicon.vue";
    //@ts-expect-error No declaration file
    import FlowRun from "../flows/FlowRun.vue";
    import DateAgo from "../layout/DateAgo.vue";
    import DataTable from "../layout/DataTable.vue";
    import TopNavBar from "../layout/TopNavBar.vue";
    import BulkSelect from "../layout/BulkSelect.vue";
    import LogsWrapper from "../logs/LogsWrapper.vue";
    //@ts-expect-error No declaration file
    import SelectTable from "../layout/SelectTable.vue";
    import TriggerAvatar from "../flows/TriggerAvatar.vue";
    import KSFilter from "../filter/components/KSFilter.vue";
    import MarkdownTooltip from "../layout/MarkdownTooltip.vue";
    import useRouteContext from "../../composables/useRouteContext";

    const triggerFilter = useTriggerFilter();


    const route = useRoute();
    const toast = useToast();
    const {t} = useI18n({useScope: "global"});

    const authStore = useAuthStore();
    const flowStore = useFlowStore();
    const triggerStore = useTriggerStore();
    const executionsStore = useExecutionsStore();

    const dataTable = ref();
    const selectTable = ref();

    const total = ref();
    const triggers = ref<any[]>([]);
    const triggerToUnlock = ref();
    const isBackfillOpen = ref(false);
    const selectedTrigger = ref(null);
    const backfill = ref<{
        start: Date | null;
        end: Date | null;
        inputs: any;
        labels: any[];
    }>({
        start: null,
        end: null,
        inputs: null,
        labels: []
    });

    const optionalColumns = computed(() => [
        {
            label: t("flow"),
            prop: "flowId",
            default: true,
            description: t("filter.table_column.triggers.flow")
        },
        {
            label: t("namespace"),
            prop: "namespace",
            default: true,
            description: t("filter.table_column.triggers.namespace")
        },
        {
            label: t("current execution"),
            prop: "executionId",
            default: false,
            description: t("filter.table_column.triggers.current execution")
        },
        {
            label: t("workerId"),
            prop: "workerId",
            default: false,
            description: t("filter.table_column.triggers.workerId")
        },
        {
            label: t("last trigger date"),
            prop: "date",
            default: true,
            description: t("filter.table_column.triggers.last trigger date")
        },
        {
            label: t("context updated date"),
            prop: "updatedDate",
            default: false,
            description: t("filter.table_column.triggers.context updated date")
        },
        {
            label: t("next evaluation date"),
            prop: "nextExecutionDate",
            default: false,
            description: t("filter.table_column.triggers.next evaluation date")
        },
        {
            label: t("evaluation lock date"),
            prop: "evaluateRunningDate",
            default: false,
            description: t("filter.table_column.triggers.evaluation lock date")
        }
    ]);

    const storageKey = storageKeys.DISPLAY_TRIGGERS_COLUMNS;

    const {visibleColumns: displayColumns, updateVisibleColumns} = useTableColumns({
        columns: optionalColumns.value,
        storageKey,
        initialVisibleColumns: optionalColumns.value.filter(col => col.default).map(col => col.prop)
    });

    const visibleColumns = computed(() =>
        displayColumns.value
            .map(prop => optionalColumns.value.find(c => c.prop === prop))
            .filter(Boolean) as ColumnConfig[]
    );

    const loadData = (callback?: () => void) => {
        const query = loadQuery({
            size: parseInt(String(route.query?.size ?? "25")),
            page: parseInt(String(route.query?.page ?? "1")),
            sort: String(route.query?.sort ?? "triggerId:asc")
        });

        const previousSelection = selection.value;
        triggerStore.search(query).then(async triggersData => {
            triggers.value = triggersData?.results;
            total.value = triggersData?.total;

            if (previousSelection && selectTable.value) {
                await selectTable.value.waitTableRender();
                selectTable.value.setSelection(previousSelection);
            }

            if (callback) {
                callback();
            }
        });
    };

    const {ready, onSort, onPageChanged, queryWithFilter, load} = useDataTableActions({
        dataTableRef: dataTable,
        loadData
    });

    const {
        queryBulkAction,
        selection,
        handleSelectionChange,
        toggleAllUnselected,
        toggleAllSelection
    } = useSelectTableActions({
        dataTableRef: selectTable
    });

    const routeInfo = computed(() => ({
        title: t("triggers")
    }));

    useRouteContext(routeInfo);

    const updateDisplayColumns = (newColumns: string[]) => {
        updateVisibleColumns(newColumns);
    };

    const onSelectionChange = handleSelectionChange;

    const setBackfillModal = (trigger: any, bool: boolean) => {
        if (!trigger) {
            isBackfillOpen.value = false;
            selectedTrigger.value = null;
            return;
        }

        executionsStore.loadFlowForExecution({
            namespace: trigger.namespace,
            flowId: trigger.flowId,
            store: true
        }).then(() => {
            isBackfillOpen.value = bool;
            selectedTrigger.value = trigger;
        });
    };

    const postBackfill = () => {
        triggerStore.update({
            ...(selectedTrigger.value as unknown as object),
            backfill: backfill.value
        })
            .then(newTrigger => {
                toast.saved(newTrigger.id);
                triggers.value = triggers.value?.map((t: any) => {
                    if (t.id === newTrigger.triggerId) {
                        return newTrigger;
                    }
                    return t;
                });
                setBackfillModal(null, false);
                backfill.value = {
                    start: null,
                    end: null,
                    inputs: null,
                    labels: []
                };
            });
    };

    const hasLogsContent = (row: any) => {
        return row.logs && row.logs.length > 0;
    };

    const getClasses = (row: any) => {
        return hasLogsContent(row) ? "expandable" : "no-expand";
    };

    const disabledStartDate = (time: Date) => {
        return new Date() < time || (backfill.value.end && time > backfill.value.end);
    };

    const disabledEndDate = (time: Date) => {
        return new Date() < time || (backfill.value.start && backfill.value.start > time);
    };

    const triggerLoadDataAfterBulkEditAction = () => {
        loadData();
        setTimeout(() => loadData(), 200);
        setTimeout(() => loadData(), 1000);
        setTimeout(() => loadData(), 5000);
    };

    const unlock = async () => {
        const namespace = triggerToUnlock.value?.namespace;
        const flowId = triggerToUnlock.value?.flowId;
        const triggerId = triggerToUnlock.value?.triggerId;
        const unlockedTrigger = await triggerStore.unlock({
            namespace: namespace,
            flowId: flowId,
            triggerId: triggerId
        });

        ElMessage({
            message: t("unlock trigger.success"),
            type: "success"
        });

        const triggerIdx = triggers.value?.findIndex((trigger: any) => trigger.namespace === namespace && trigger.flowId === flowId && trigger.triggerId === triggerId);
        if (triggerIdx !== -1) {
            triggers.value[triggerIdx] = unlockedTrigger;
        }

        triggerToUnlock.value = undefined;
    };

    const setDisabled = (trigger: any, value: boolean) => {
        if (trigger.codeDisabled) {
            ElMessage({
                message: t("triggerflow disabled"),
                type: "error",
                showClose: true,
                duration: 1500
            });
            return;
        }
        triggerStore.update({...trigger, disabled: !value})
            .then(updatedTrigger => {
                toast.saved(updatedTrigger.triggerId);
                triggers.value = triggers.value?.map((t: any) => {
                    const triggerContextMatches = t.triggerContext &&
                        t.triggerContext.flowId === updatedTrigger.flowId &&
                        t.triggerContext.triggerId === updatedTrigger.triggerId;

                    if (triggerContextMatches) {
                        return {triggerContext: updatedTrigger, abstractTrigger: t.abstractTrigger};
                    }
                    return t;
                });
            });
    };

    const confirmDeleteTrigger = (trigger: TriggerDeleteOptions) => {
        toast.confirm(
            t("delete trigger confirmation", {id: trigger.id}),
            () => triggerStore.delete({
                namespace: trigger.namespace,
                flowId: trigger.flowId,
                triggerId: trigger.triggerId
            }).then(() => {
                toast.success(t("delete trigger success", {id: trigger.id}));
                loadData();
            }).catch(error => {
                toast.error(t("delete trigger error", {id: trigger.id}));
                console.error(error);
            }),
            "warning"
        );
    };

    const deleteTriggers = () => {
        genericConfirmAction(
            "bulk delete triggers",
            "deleteByQuery",
            "deleteByTriggers",
            "bulk success delete triggers",
            null,
            "WARNING: deleting triggers may lead to duplicate executions if the triggers are still active in flows"
        );
    };

    const genericConfirmAction = (toastKey: string, queryAction: string, byIdAction: string, success: string, data?: any, extraWarning?: string) => {
        let message = t(toastKey, {"count": queryBulkAction.value ? total.value : selection.value?.length}) + ". " + t("bulk action async warning");

        if (extraWarning) {
            message += "<br><br><strong>" + extraWarning + "</strong>";
        }

        toast.confirm(
            message,
            () => genericConfirmCallback(queryAction, byIdAction, success, data)
        );
    };

    const genericConfirmCallback = (queryAction: string, byIdAction: string, success: string, data?: any) => {
        const actionMap: Record<string, () => any> = {
            "unpauseBackfillByQuery": () => triggerStore.unpauseBackfillByQuery,
            "unpauseBackfillByTriggers": () => triggerStore.unpauseBackfillByTriggers,
            "pauseBackfillByQuery": () => triggerStore.pauseBackfillByQuery,
            "pauseBackfillByTriggers": () => triggerStore.pauseBackfillByTriggers,
            "deleteBackfillByQuery": () => triggerStore.deleteBackfillByQuery,
            "deleteBackfillByTriggers": () => triggerStore.deleteBackfillByTriggers,
            "unlockByQuery": () => triggerStore.unlockByQuery,
            "unlockByTriggers": () => triggerStore.unlockByTriggers,
            "setDisabledByQuery": () => triggerStore.setDisabledByQuery,
            "setDisabledByTriggers": () => triggerStore.setDisabledByTriggers,
            "deleteByQuery": () => triggerStore.deleteByQuery,
            "deleteByTriggers": () => triggerStore.deleteByTriggers,
        };

        if (queryBulkAction.value) {
            const query = loadQuery({});
            const options = {...query, ...data};
            const actions = actionMap[queryAction]();
            return actions(options)
                .then((data: any) => {
                    toast.success(t(success, {count: data?.count}));
                    toggleAllUnselected();
                    triggerLoadDataAfterBulkEditAction();
                });
        } else {
            const selectionData = selection.value;
            const options = {triggers: selectionData, ...data};
            const actions = actionMap[byIdAction]();
            return actions(byIdAction.includes("setDisabled") ? options : selectionData)
                .then((data: any) => {
                    toast.success(t(success, {count: data?.count}));
                    toggleAllUnselected();
                    triggerLoadDataAfterBulkEditAction();
                }).catch((e: any) => {
                    toast.error(e?.invalids?.map((exec: any) => {
                        return {message: t(exec?.message, {triggers: exec?.invalidValue})}
                    }), t(e?.message));
                });
        }
    };

    const unpauseBackfills = () => {
        genericConfirmAction(
            "bulk unpause backfills",
            "unpauseBackfillByQuery",
            "unpauseBackfillByTriggers",
            "bulk success unpause backfills"
        );
    };

    const pauseBackfills = () => {
        genericConfirmAction(
            "bulk pause backfills",
            "pauseBackfillByQuery",
            "pauseBackfillByTriggers",
            "bulk success pause backfills"
        );
    };

    const deleteBackfills = () => {
        genericConfirmAction(
            "bulk delete backfills",
            "deleteBackfillByQuery",
            "deleteBackfillByTriggers",
            "bulk success delete backfills"
        );
    };

    const unlockTriggers = () => {
        genericConfirmAction(
            "bulk unlock",
            "unlockByQuery",
            "unlockByTriggers",
            "bulk success unlock"
        );
    };

    const setDisabledTriggers = (bool: boolean) => {
        genericConfirmAction(
            `bulk disabled status.${bool}`,
            "setDisabledByQuery",
            "setDisabledByTriggers",
            `bulk success disabled status.${bool}`,
            {disabled: bool}
        );
    };

    const loadQuery = (base: any) => {
        const queryFilter = queryWithFilter();

        const timeRange = queryFilter["filters[timeRange][EQUALS]"];
        if (timeRange) {
            const end = new Date();
            const start = new Date(end.getTime() - moment.duration(timeRange).asMilliseconds());
            queryFilter["filters[startDate][GREATER_THAN_OR_EQUAL_TO]"] = start.toISOString();
            queryFilter["filters[endDate][LESS_THAN_OR_EQUAL_TO]"] = end.toISOString();
            delete queryFilter["filters[timeRange][EQUALS]"];
        }

        return _merge(base, queryFilter);
    };

    const checkBackfill = computed(() => {
        if (!backfill.value?.start) {
            return true;
        }
        if (backfill.value?.end && backfill.value.start > backfill.value.end) {
            return true;
        }
        if (flowStore.flow?.inputs) {
            const requiredInputs = flowStore.flow.inputs?.map((input: any) => input?.required !== false ? input?.id : null).filter((i: any) => i !== null) || [];

            if (requiredInputs.length > 0) {
                if (!backfill.value?.inputs) {
                    return true;
                }
                const fillInputs = Object.keys(backfill.value.inputs).filter((i: string) => backfill.value?.inputs?.[i] !== null && backfill.value?.inputs?.[i] !== undefined);
                if (requiredInputs.sort().join(",") !== fillInputs.sort().join(",")) {
                    return true;
                }
            }
        }
        if (backfill.value?.labels?.length > 0) {
            for (let label of backfill.value.labels) {
                if (((label as any)?.key && !(label as any)?.value) || (!(label as any)?.key && (label as any)?.value)) {
                    return true;
                }
            }
        }
        return false;
    });

    const triggersMerged = computed(() => {
        const all = triggers.value?.map((t: any) => {
            return {
                ...t?.abstractTrigger,
                ...t?.triggerContext,
                codeDisabled: t?.abstractTrigger?.disabled,
                missingSource: !t?.abstractTrigger
            };
        }) ?? [];

        return all;
    });

    watch(ready, (newReady: any) => {
        if (newReady) {
            loadData(load);
        }
    });

    async function exportTriggersAsStream() {
        await triggerStore.exportTriggersAsCSV(route.query);
    }
</script>

<style scoped lang="scss">
    .data-table-wrapper {
        margin-left: 0 !important;
        padding-left: 0 !important;
    }

    .backfillContainer {
        display: flex;
        align-items: center;
    }

    .action-container {
        display: flex;
        align-items: center;
        gap: 5px;
    }

    .statusIcon {
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

    .el-table {
        a {
            color: var(--ks-content-link);
        }
    }

    .wide-tooltip {
        max-width: 400px;
        white-space: normal;
        word-break: break-word;
        color: var(--ks-content-primary) !important;
    }

    :deep(.el-collapse) {
        border-radius: var(--bs-border-radius-lg);
        border: 1px solid var(--ks-border-primary);
        background: var(--bs-gray-100);

        .el-collapse-item__header {
            background: transparent;
            border-bottom: 1px solid var(--ks-border-primary);
            font-size: var(--bs-font-size-sm);
        }

        .el-collapse-item__content {
            background: var(--bs-gray-100);
            border-bottom: 1px solid var(--ks-border-primary);
        }

        .el-collapse-item__header,
        .el-collapse-item__content {
            &:last-child {
                border-bottom-left-radius: var(--bs-border-radius-lg);
                border-bottom-right-radius: var(--bs-border-radius-lg);
            }
        }
    }
</style>