<template>
    <TopNavBar :title="routeInfo.title">
        <template #actions>
            <KsButton :icon="Download" @click="exportTriggersAsStream()">
                {{ $t('export_csv') }}
            </KsButton>
        </template>
    </TopNavBar>
    <section class="container">
        <div>
            <KsDataTable
                ref="dataTable"
                :loadData="loadData"
                :data="triggersMerged"
                :total="total"
                @page-changed="({page, size}: {page: number; size: number}) => router.push({query: {...route.query, page: String(page), size: String(size)}})"
                @sort-change="({prop, order}: {prop: string; order: string | null}) => router.push({query: {...route.query, sort: `${prop}:${order === 'descending' ? 'desc' : 'asc'}`}})"
                @ready="ready = true"
                :defaultSort="{prop: 'flowId', order: 'ascending'}"
                expandable
                :rowClassName="getClasses"
                :no-data-text="$t('no_results.triggers')"
                :rowKey="(row: any) => `${row.namespace}-${row.flowId}-${row.triggerId}`"
            >
                <template #navbar>
                    <KSFilter
                        :prefix="'triggers'"
                        :configuration="triggerFilter"
                        @update-properties="updateDisplayColumns"
                        :tableOptions="{
                            chart: {shown: false},
                            refresh: {shown: true, callback: () => dataTable?.reload()}
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
                <template #expand>
                    <KsTableColumn type="expand">
                        <template #default="props">
                            <LogsWrapper
                                class="m-3"
                                :filters="props.row"
                                v-if="hasLogsContent(props.row)"
                                :withCharts="false"
                                embed
                            />
                        </template>
                    </KsTableColumn>
                </template>
                <template #bulk-actions>
                    <KsButton @click="setDisabledTriggers(false)">
                        {{ $t("enable") }}
                    </KsButton>
                    <KsButton @click="setDisabledTriggers(true)">
                        {{ $t("disable") }}
                    </KsButton>
                    <KsButton @click="unlockTriggers()">
                        {{ $t("unlock") }}
                    </KsButton>
                    <KsButton @click="pauseBackfills()">
                        {{ $t("pause backfills") }}
                    </KsButton>
                    <KsButton @click="unpauseBackfills()">
                        {{ $t("continue backfills") }}
                    </KsButton>
                    <KsButton @click="deleteBackfills()">
                        {{ $t("delete backfills") }}
                    </KsButton>
                    <KsButton @click="deleteTriggers()">
                        {{ $t("delete triggers") }}
                    </KsButton>
                </template>
                <KsTableColumn
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
                </KsTableColumn>

                <KsTableColumn
                    v-for="col in visibleColumns"
                    :key="col.prop"
                    :prop="col.prop"
                    :label="col.label"
                    :sortable="['flowId', 'namespace', 'nextEvaluationDate'].includes(col.prop) ? 'custom' : false"
                    :sortOrders="['flowId', 'namespace', 'nextEvaluationDate'].includes(col.prop) ? ['ascending', 'descending'] : undefined"
                >
                    <template #header v-if="col.prop === 'lastTriggeredDate'">
                        <KsTooltip
                            :content="$t('last trigger date tooltip')"
                            placement="top"
                            popperClass="wide-tooltip"
                        >
                            <span>{{ col.label }}</span>
                        </KsTooltip>
                    </template>
                    <template #header v-else-if="col.prop === 'updatedAt'">
                        <KsTooltip
                            :content="$t('context updated date tooltip')"
                            placement="top"
                            popperClass="wide-tooltip"
                        >
                            <span>{{ col.label }}</span>
                        </KsTooltip>
                    </template>
                    <template #header v-else-if="col.prop === 'nextExecutionDate'">
                        <KsTooltip
                            :content="$t('next evaluation date tooltip')"
                            placement="top"
                            popperClass="wide-tooltip"
                        >
                            <span>{{ col.label }}</span>
                        </KsTooltip>
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
                        <template v-else-if="col.prop === 'workerId'">
                            <KsId
                                :value="scope.row.workerId"
                                :shrink="true"
                            />
                        </template>
                        <template v-else-if="col.prop === 'lastTriggeredDate'">
                            <KsDateAgo :inverted="true" :date="scope.row.lastTriggeredDate" />
                        </template>
                        <template v-else-if="col.prop === 'updatedAt'">
                            <KsDateAgo :inverted="true" :date="scope.row.updatedAt" />
                        </template>
                        <template v-else-if="col.prop === 'nextEvaluationDate'">
                            <KsDateAgo :inverted="true" :date="scope.row.nextEvaluationDate" />
                        </template>
                    </template>
                </KsTableColumn>

                <KsTableColumn :label="$t('details')">
                    <template #default="scope">
                        <TriggerAvatar
                            v-if="!scope.row.missingSource"
                            :flow="{id: scope.row.flowId, namespace: scope.row.namespace, triggers: [scope.row]}"
                            :triggerId="scope.row.id"
                        />
                    </template>
                </KsTableColumn>

                <KsTableColumn
                    v-if="authStore.user?.hasAnyAction(resource.EXECUTION, action.UPDATE)"
                    columnKey="action"
                    className="row-action"
                >
                    <template #default="scope">
                        <div class="action-container">
                            <KsIconButton
                                v-if="scope.row.locked"
                                :tooltip="$t('unlock trigger.tooltip.evaluation')"
                                placement="left"
                                @click="triggerToUnlock = scope.row"
                            >
                                <LockOff />
                            </KsIconButton>
                            <KsIconButton
                                :tooltip="$t('delete trigger')"
                                placement="left"
                                @click="confirmDeleteTrigger(scope.row)"
                            >
                                <Delete />
                            </KsIconButton>
                        </div>
                    </template>
                </KsTableColumn>
                <KsTableColumn :label="$t('backfill')" columnKey="backfill">
                    <template #default="scope">
                        <div class="backfillContainer items-center gap-2">
                            <span v-if="scope.row.backfill" class="statusIcon">
                                <KsTooltip
                                    v-if="!scope.row.backfill.paused"
                                    :content="$t('backfill running')"
                                >
                                    <PlayBox font />
                                </KsTooltip>
                                <KsTooltip v-else :content="$t('backfill paused')">
                                    <PauseBox />
                                </KsTooltip>
                            </span>

                            <KsButton
                                :icon="CalendarCollapseHorizontalOutline"
                                v-if="authStore.user?.hasAnyAction(resource.EXECUTION, action.UPDATE)"
                                @click="setBackfillModal(scope.row, true)"
                                size="small"
                                type="primary"
                                :disabled="scope.row.disabled || scope.row.codeDisabled"
                            >
                                {{ $t("backfill executions") }}
                            </KsButton>
                        </div>
                    </template>
                </KsTableColumn>


                <KsTableColumn :label="$t('enabled')" columnKey="disable" className="row-action">
                    <template #default="scope">
                        <KsTooltip
                            v-if="!scope.row.missingSource"
                            :content="$t('trigger disabled')"
                            :disabled="!scope.row.codeDisabled"
                        >
                            <KsSwitch
                                :modelValue="!(scope.row.disabled || scope.row.codeDisabled)"
                                @change="setDisabled(scope.row, $event as boolean)"
                                inlinePrompt
                                class="switch-text"
                                :disabled="scope.row.codeDisabled"
                            />
                        </KsTooltip>
                        <KsTooltip v-else :content="$t('flow source not found')">
                            <AlertCircle />
                        </KsTooltip>
                    </template>
                </KsTableColumn>
            </KsDataTable>

            <KsDialog v-model="triggerToUnlock" destroyOnClose :appendToBody="true">
                <template #header>
                    <span v-html="$t('unlock trigger.confirmation')" />
                </template>
                {{ $t("unlock trigger.warning") }}
                <template #footer>
                    <KsButton :icon="LockOff" @click="unlock" type="primary">
                        {{ $t("unlock trigger.button") }}
                    </KsButton>
                </template>
            </KsDialog>

            <KsDialog v-model="isBackfillOpen" destroyOnClose :appendToBody="true">
                <template #header>
                    <span v-html="$t('backfill executions')" />
                </template>
                <KsForm :model="backfill" labelPosition="top">
                    <div class="pickers">
                        <div class="small-picker">
                            <KsFormItem label="Start">
                                <KsDatePicker
                                    v-model="backfill.start"
                                    type="datetime"
                                    placeholder="Start"
                                    :disabledDate="disabledStartDate"
                                />
                            </KsFormItem>
                        </div>
                        <div class="small-picker">
                            <KsFormItem label="End">
                                <KsDatePicker
                                    v-model="backfill.end"
                                    type="datetime"
                                    placeholder="End"
                                    :disabledDate="disabledEndDate"
                                />
                            </KsFormItem>
                        </div>
                    </div>
                </KsForm>
                <FlowRun
                    @update-inputs="backfill.inputs = $event"
                    @update-labels="backfill.labels = $event"
                    :selectedTrigger="selectedTrigger"
                    :redirect="false"
                    :embed="true"
                />
                <template #footer>
                    <KsButton
                        type="primary"
                        @click="postBackfill()"
                        :disabled="checkBackfill"
                    >
                        {{ $t("execute backfill") }}
                    </KsButton>
                </template>
            </KsDialog>
        </div>
    </section>
</template>
<script setup lang="ts">
    import _merge from "lodash/merge";
    import {ref, computed, watch, useTemplateRef} from "vue";
    import moment from "moment";
    import {useI18n} from "vue-i18n";
    import {useRoute, useRouter} from "vue-router";
    import {KsMessage} from "@kestra-io/design-system";
    import {useToast} from "../../utils/toast";
    import {useFlowStore} from "../../stores/flow";
    import {useAuthStore} from "override/stores/auth";
    import {invisibleSpace} from "../../utils/filters";
    import {storageKeys} from "../../utils/constants";
    import {TriggerDeleteOptions, useTriggerStore} from "../../stores/trigger";
    import {useExecutionsStore} from "../../stores/executions";
    import {useTriggerFilter} from "../filter/configurations";
    import {type ColumnConfig, useTableColumns} from "../../composables/useTableColumns";
    import useRestoreUrl from "../../composables/useRestoreUrl";

    const {loadInit} = useRestoreUrl();

    import action from "../../models/action";
    import resource from "../../models/resource";
    import LockOff from "vue-material-design-icons/LockOff.vue";
    import PlayBox from "vue-material-design-icons/PlayBox.vue";
    import PauseBox from "vue-material-design-icons/PauseBox.vue";
    import AlertCircle from "vue-material-design-icons/AlertCircle.vue";
    import CalendarCollapseHorizontalOutline from "vue-material-design-icons/CalendarCollapseHorizontalOutline.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import Download from "vue-material-design-icons/Download.vue";

    import {KsId, KsIconButton} from "@kestra-io/design-system";
    //@ts-expect-error No declaration file
    import FlowRun from "../flows/FlowRun.vue";
    import TopNavBar from "../layout/TopNavBar.vue";

    import LogsWrapper from "../logs/LogsWrapper.vue";
    import TriggerAvatar from "../flows/TriggerAvatar.vue";
    import {KsFilter as KSFilter} from "@kestra-io/design-system";
    import MarkdownTooltip from "../layout/MarkdownTooltip.vue";
    import useRouteContext from "../../composables/useRouteContext";

    const triggerFilter = useTriggerFilter();


    const route = useRoute();
    const router = useRouter();
    const toast = useToast();
    const {t} = useI18n({useScope: "global"});

    const authStore = useAuthStore();
    const flowStore = useFlowStore();
    const triggerStore = useTriggerStore();
    const executionsStore = useExecutionsStore();

    const dataTable = useTemplateRef("dataTable");

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
            label: t("workerId"),
            prop: "workerId",
            default: false,
            description: t("filter.table_column.triggers.workerId")
        },
        {
            label: t("last trigger date"),
            prop: "lastTriggeredDate",
            default: true,
            description: t("filter.table_column.triggers.last trigger date")
        },
        {
            label: t("state updated date"),
            prop: "updatedAt",
            default: false,
            description: t("filter.table_column.triggers.context updated date")
        },
        {
            label: t("next evaluation date"),
            prop: "nextEvaluationDate",
            default: false,
            description: t("filter.table_column.triggers.next evaluation date")
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

    const ready = ref(false);

    const loadData = async ({page, size, sort}: {page: number; size: number; sort?: string}) => {
        if (!loadInit.value) return;
        const query = loadQuery({
            size,
            page,
            sort: sort ?? String(route.query.sort ?? "triggerId:asc")
        });

        const previousSelection = selection.value;
        await triggerStore.search(query).then(async triggersData => {
            triggers.value = triggersData?.results;
            total.value = triggersData?.total;

            if (previousSelection && dataTable.value) {
                await dataTable.value.waitTableRender();
                dataTable.value.setSelection(previousSelection);
            }
        });
    };

    const filterQuery = computed(() => {
        const {page: _p, size: _s, sort: _so, ...filters} = route.query;
        return filters;
    });
    watch(filterQuery, () => {
        dataTable.value?.resetAndReload();
    }, {deep: true});

    const selection = computed(() => dataTable.value?.selection ?? []);
    const queryBulkAction = computed(() => dataTable.value?.queryBulkAction ?? false);
    const toggleAllUnselected = () => dataTable.value?.toggleAllUnselected();


    const routeInfo = computed(() => ({
        title: t("triggers")
    }));

    useRouteContext(routeInfo);

    const updateDisplayColumns = (newColumns: string[]) => {
        updateVisibleColumns(newColumns);
    };

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

    const cleanBackfill = computed(() => {
        const labels = backfill.value.labels?.filter((label: any) => label.key && label.value);
        return {...backfill.value, labels: labels?.length ? labels : null};
    });

    const postBackfill = () => {
        const trigger = selectedTrigger.value as any;
        triggerStore.createBackfill({
            namespace: trigger.namespace,
            flowId: trigger.flowId,
            triggerId: trigger.triggerId,
            backfill: cleanBackfill.value
        })
            .then(() => {
                toast.saved(trigger?.triggerId);
                setBackfillModal(null, false);
                backfill.value = {
                    start: null,
                    end: null,
                    inputs: null,
                    labels: []
                };
                triggerLoadDataAfterBulkEditAction();
            });
    };

    const hasLogsContent = (row: any) => {
        return row.logs && row.logs.length > 0;
    };

    const getClasses = (row: any) => {
        return hasLogsContent(row) ? "expandable" : "no-expand";
    };

    const disabledStartDate = (time: Date): boolean => {
        return new Date() < time || !!(backfill.value.end && time > backfill.value.end);
    };

    const disabledEndDate = (time: Date): boolean => {
        return new Date() < time || !!(backfill.value.start && backfill.value.start > time);
    };

    const triggerLoadDataAfterBulkEditAction = () => {
        dataTable.value?.reload();
        setTimeout(() => dataTable.value?.reload(), 200);
        setTimeout(() => dataTable.value?.reload(), 1000);
        setTimeout(() => dataTable.value?.reload(), 5000);
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

        KsMessage({
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
            KsMessage({
                message: t("triggerflow disabled"),
                type: "error",
                showClose: true,
                duration: 1500
            });
            return;
        }
        triggerStore.setDisabled({...trigger, disabled: !value})
            .then((updatedTrigger: any) => {
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
                dataTable.value?.reload();
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
        const {page: _p, size: _s, sort: _so, ...queryFilter} = route.query as Record<string, any>;

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
                ...t?.trigger,
                ...t?.state,
                codeDisabled: t?.trigger?.disabled,
                missingSource: !t?.trigger
            };
        }) ?? [];

        return all;
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

    :deep(.kel-table__expand-icon) {
        pointer-events: none;

        .kel-icon {
            display: none;
        }
    }

    :deep(.kel-switch) {
        .is-text {
            padding: 0 3px;
            color: inherit;
        }

        &.is-checked {
            .is-text {
                color: var(--ks-white);
            }
        }
    }

    .kel-table {
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

    :deep(.kel-collapse) {
        border-radius: var(--kel-border-radius-round);
        border: 1px solid var(--ks-border-primary);
        background: var(--ks-tag-background);

        .kel-collapse-item__header {
            background: transparent;
            border-bottom: 1px solid var(--ks-border-primary);
            font-size: var(--ks-font-size-sm);
        }

        .kel-collapse-item__content {
            background: var(--ks-tag-background);
            border-bottom: 1px solid var(--ks-border-primary);
        }

        .kel-collapse-item__header,
        .kel-collapse-item__content {
            &:last-child {
                border-bottom-left-radius: var(--kel-border-radius-round);
                border-bottom-right-radius: var(--kel-border-radius-round);
            }
        }
    }
</style>
