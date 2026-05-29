<template>
    <div class="triggers-manage">
        <KsDataTable
            ref="dataTable"
            :loadData="loadData"
            :data="triggersMerged"
            :total="total"
            :currentPage="urlPage"
            :pageSize="urlSize"
            :defaultSort="{prop: 'flowId', order: 'ascending'}"
            :selectable="canCheck"
            :selectionMapper="selectionMapper"
            :rowClassName="getClasses"
            :rowKey="(row: any) => `${row.namespace}-${row.flowId}-${row.triggerId}`"
            :forceExpandedRowKeys="expandedRowKeys"
            :no-data-text="$t('no_results.triggers')"
            @page-changed="({page, size}: {page: number; size: number}) => router.push({query: {...route.query, page: String(page), size: String(size)}})"
            @sort-change="({prop, order}: {prop: string; order: string | null}) => router.push({query: {...route.query, sort: `${prop}:${order === 'descending' ? 'desc' : 'asc'}`}})"
        >
            <template #navbar>
                <KsFilter
                    :prefix="'triggers'"
                    :configuration="triggerFilter"
                    @update-properties="updateDisplayColumns"
                    :tableOptions="{
                        chart: {shown: false},
                        refresh: {shown: true, callback: refresh}
                    }"
                    :properties="{
                        displayColumns,
                        shown: true,
                        columns: optionalColumns,
                        storageKey
                    }"
                    :defaultScope="false"
                    :defaultTimeRange="false"
                />
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

            <KsTableColumn type="expand">
                <template #default="props">
                    <BackfillBanner
                        v-if="props.row.backfill"
                        :row="props.row"
                        @pause="pauseBackfill(props.row)"
                        @resume="unpauseBackfill(props.row)"
                        @stop="deleteBackfill(props.row)"
                    />
                    <LogsWrapper
                        v-if="hasLogsContent(props.row)"
                        class="m-3"
                        :filters="props.row"
                        :withCharts="false"
                        embed
                    />
                </template>
            </KsTableColumn>

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
                :sortable="SORTABLE_COLUMNS.includes(col.prop) ? 'custom' : false"
                :sortOrders="SORTABLE_COLUMNS.includes(col.prop) ? ['ascending', 'descending'] : undefined"
            >
                <template #header v-if="DATE_TOOLTIP_KEYS[col.prop]">
                    <span class="header-with-tooltip">
                        <span>{{ col.label }}</span>
                        <KsTooltip
                            :content="$t(DATE_TOOLTIP_KEYS[col.prop])"
                            placement="top"
                            effect="light"
                            popperClass="wide-tooltip"
                        >
                            <InformationOutline class="header-tooltip-icon" />
                        </KsTooltip>
                    </span>
                </template>
                <template #default="scope">
                    <template v-if="col.prop === 'flowId'">
                        <router-link
                            v-if="scope.row.namespace && scope.row.flowId"
                            :to="{name: 'flows/update', params: {tenant: route.params?.tenant, namespace: scope.row.namespace, id: scope.row.flowId}}"
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
                        <KsId :value="scope.row.workerId" :shrink="true" />
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
                    <template v-else-if="col.prop === 'evaluatedAt'">
                        <KsDateAgo :inverted="true" :date="scope.row.evaluatedAt" />
                    </template>
                </template>
            </KsTableColumn>

            <KsTableColumn :label="$t('backfill')" columnKey="backfill">
                <template #default="scope">
                    <template v-if="scope.row.backfill">
                        <KsTag
                            size="small"
                            :type="scope.row.backfill.paused ? 'warning' : 'info'"
                            effect="light"
                            class="backfill-tag"
                        >
                            {{ scope.row.backfill.paused ? $t("paused") : $t("running") }}
                        </KsTag>
                    </template>
                    <template v-else-if="isSchedule(scope.row.type) && authStore.user?.hasAnyAction(resource.EXECUTION, action.UPDATE)">
                        <KsButton
                            :icon="CalendarCollapseHorizontalOutline"
                            @click="setBackfillModal(scope.row, true)"
                            size="small"
                            type="primary"
                            :disabled="scope.row.disabled || scope.row.codeDisabled"
                        >
                            {{ $t("backfill executions") }}
                        </KsButton>
                    </template>
                </template>
            </KsTableColumn>

            <KsTableColumn :label="$t('enabled')" columnKey="disable" className="row-action">
                <template #default="scope">
                    <KsTooltip
                        v-if="!scope.row.missingSource"
                        :content="$t('trigger disabled')"
                        :disabled="!scope.row.codeDisabled"
                        effect="light"
                    >
                        <KsSwitch
                            :modelValue="!(scope.row.disabled || scope.row.codeDisabled)"
                            @change="(value: string | number | boolean) => setDisabled(scope.row, Boolean(value))"
                            inlinePrompt
                            class="switch-text"
                            :disabled="scope.row.codeDisabled"
                        />
                    </KsTooltip>
                    <KsTooltip v-else :content="$t('flow source not found')" effect="light">
                        <AlertCircle />
                    </KsTooltip>
                </template>
            </KsTableColumn>

            <KsTableColumn
                v-if="authStore.user?.hasAnyAction(resource.EXECUTION, action.UPDATE)"
                columnKey="row-actions"
                className="row-action"
            >
                <template #default="scope">
                    <KsDropdown trigger="click" placement="bottom-end">
                        <KsButton
                            :icon="DotsVertical"
                            link
                            size="small"
                            :aria-label="$t('actions')"
                        />
                        <template #dropdown>
                            <KsDropdownMenu>
                                <KsDropdownItem @click="openDetails(scope.row)">
                                    <TextSearch class="mr-1" />
                                    {{ $t("details") }}
                                </KsDropdownItem>
                                <KsDropdownItem
                                    :disabled="!scope.row.locked"
                                    @click="restart(scope.row)"
                                >
                                    <Restart class="mr-1" />
                                    {{ $t("restart") }}
                                </KsDropdownItem>
                                <KsDropdownItem
                                    :disabled="!scope.row.locked"
                                    @click="unlock(scope.row)"
                                >
                                    <LockOff class="mr-1" />
                                    {{ $t("unlock") }}
                                </KsDropdownItem>
                                <KsDropdownItem
                                    divided
                                    class="danger"
                                    @click="confirmDeleteTrigger(scope.row)"
                                >
                                    <Delete class="mr-1" />
                                    {{ $t("delete") }}
                                </KsDropdownItem>
                            </KsDropdownMenu>
                        </template>
                    </KsDropdown>
                </template>
            </KsTableColumn>
        </KsDataTable>

        <KsDrawer v-if="isDetailsOpen" v-model="isDetailsOpen">
            <template #header>
                <code>{{ detailsTriggerId }}</code>
            </template>
            <KsMarkdown v-if="detailsTrigger?.description" :content="detailsTrigger.description" />
            <Vars :data="detailsData" />
        </KsDrawer>

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
</template>

<script setup lang="ts">
    import _merge from "lodash/merge"
    import {ref, computed, watch, useTemplateRef} from "vue"
    import moment from "moment"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import {KsMessage, KsDrawer, KsMarkdown, KsTag, KsDropdown, KsDropdownMenu, KsDropdownItem} from "@kestra-io/design-system"
    import {useToast} from "../../../utils/toast"
    import {useFlowStore} from "../../../stores/flow"
    import {useAuthStore} from "override/stores/auth"
    import {invisibleSpace} from "../../../utils/filters"
    import {storageKeys} from "../../../utils/constants"
    import {TriggerDeleteOptions, useTriggerStore} from "../../../stores/trigger"
    import {useExecutionsStore} from "../../../stores/executions"
    import {useTriggerFilter} from "../../filter/configurations"
    import {type ColumnConfig, useTableColumns} from "../../../composables/useTableColumns"
    import useRestoreUrl from "../../../composables/useRestoreUrl"

    import action from "../../../models/action"
    import resource from "../../../models/resource"
    import LockOff from "vue-material-design-icons/LockOff.vue"
    import AlertCircle from "vue-material-design-icons/AlertCircle.vue"
    import CalendarCollapseHorizontalOutline from "vue-material-design-icons/CalendarCollapseHorizontalOutline.vue"
    import Delete from "vue-material-design-icons/Delete.vue"
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue"
    import InformationOutline from "vue-material-design-icons/InformationOutline.vue"
    import Restart from "vue-material-design-icons/Restart.vue"
    import TextSearch from "vue-material-design-icons/TextSearch.vue"

    import FlowRun, {SelectedTrigger} from "../../flows/FlowRun.vue"
    import LogsWrapper from "../../logs/LogsWrapper.vue"
    import BackfillBanner from "../../flows/BackfillBanner.vue"
    import Vars from "../../executions/Vars.vue"
    import MarkdownTooltip from "../../layout/MarkdownTooltip.vue"

    const triggerFilter = useTriggerFilter()

    const route = useRoute()
    const router = useRouter()
    const toast = useToast()
    const {t} = useI18n({useScope: "global"})

    const authStore = useAuthStore()
    const flowStore = useFlowStore()
    const triggerStore = useTriggerStore()
    const executionsStore = useExecutionsStore()

    const {loadInit} = useRestoreUrl()

    const dataTable = useTemplateRef<any>("dataTable")

    const total = ref(0)
    const triggers = ref<any[]>([])
    const isBackfillOpen = ref(false)
    const isDetailsOpen = ref(false)
    const detailsTriggerId = ref<string | undefined>()
    const selectedTrigger = ref<SelectedTrigger | undefined>()

    const DATE_COLUMNS: readonly string[] = ["lastTriggeredDate", "nextEvaluationDate", "evaluatedAt", "updatedAt"]
    const SORTABLE_COLUMNS: readonly string[] = ["flowId", "namespace", ...DATE_COLUMNS]
    const DATE_TOOLTIP_KEYS: Record<string, string> = {
        lastTriggeredDate: "last trigger date tooltip",
        updatedAt: "context updated date tooltip",
        nextEvaluationDate: "next evaluation date tooltip",
        evaluatedAt: "last evaluation date tooltip",
    }
    const backfill = ref<{
        start: Date | null;
        end: Date | null;
        inputs: any;
        labels: any[];
    }>({
        start: null,
        end: null,
        inputs: null,
        labels: [],
    })

    const optionalColumns = computed<ColumnConfig[]>(() => [
        {
            label: t("flow"),
            prop: "flowId",
            default: true,
            description: t("filter.table_column.triggers.flow"),
        },
        {
            label: t("namespace"),
            prop: "namespace",
            default: true,
            description: t("filter.table_column.triggers.namespace"),
        },
        {
            label: t("workerId"),
            prop: "workerId",
            default: false,
            description: t("filter.table_column.triggers.workerId"),
        },
        {
            label: t("last trigger date"),
            prop: "lastTriggeredDate",
            default: true,
            description: t("filter.table_column.triggers.last trigger date"),
        },
        {
            label: t("state updated date"),
            prop: "updatedAt",
            default: false,
            description: t("filter.table_column.triggers.context updated date"),
        },
        {
            label: t("next evaluation date"),
            prop: "nextEvaluationDate",
            default: false,
            description: t("filter.table_column.triggers.next evaluation date"),
        },
        {
            label: t("last evaluation date"),
            prop: "evaluatedAt",
            default: false,
            description: t("filter.table_column.triggers.last evaluation date"),
        },
    ])

    const storageKey = storageKeys.DISPLAY_TRIGGERS_COLUMNS

    const {visibleColumns: displayColumns, updateVisibleColumns} = useTableColumns({
        columns: optionalColumns.value,
        storageKey,
        initialVisibleColumns: optionalColumns.value.filter(col => col.default).map(col => col.prop),
    })

    const visibleColumns = computed(() =>
        displayColumns.value
            .map(prop => optionalColumns.value.find(c => c.prop === prop))
            .filter(Boolean) as ColumnConfig[],
    )

    const updateDisplayColumns = (newColumns: string[]) => {
        updateVisibleColumns(newColumns)
    }

    const canCheck = computed(() => authStore.user?.hasAnyAction(resource.EXECUTION, action.UPDATE) ?? false)

    const selectionMapper = (row: any) => ({
        namespace: row.namespace,
        flowId: row.flowId,
        triggerId: row.triggerId ?? row.id,
    })

    const isSchedule = (type: string) => type === "io.kestra.plugin.core.trigger.Schedule"

    const triggersMerged = computed(() => {
        return triggers.value?.map((tr: any) => ({
            ...tr?.trigger,
            ...tr?.state,
            codeDisabled: tr?.trigger?.disabled,
            missingSource: !tr?.trigger,
        })) ?? []
    })

    const expandedRowKeys = computed<string[]>(() =>
        triggersMerged.value
            .filter((row: any) => !!row.backfill)
            .map((row: any) => `${row.namespace}-${row.flowId}-${row.triggerId}`),
    )

    const detailsTrigger = computed(() =>
        triggersMerged.value.find((row: any) => row.triggerId === detailsTriggerId.value),
    )

    const detailsData = computed(() => {
        const trigger = detailsTrigger.value
        if (!trigger) return {}
        return Object
            .entries(trigger)
            .filter(([key]) => !["tenantId", "namespace", "flowId", "flowRevision", "triggerId", "description"].includes(key))
            .reduce((map, [key, value]) => {
                map[key] = value
                return map
            }, {} as any)
    })

    const selection = computed<any[]>(() => dataTable.value?.selection ?? [])
    const queryBulkAction = computed<boolean>(() => dataTable.value?.queryBulkAction ?? false)
    const toggleAllUnselected = () => dataTable.value?.toggleAllUnselected()

    const loadQuery = (base: any) => {
        const {page: _p, size: _s, sort: _so, logsPage: _lp, logsSize: _ls, ...restQuery} = route.query as Record<string, any>
        const queryFilter: Record<string, any> = {...restQuery}

        const timeRange = queryFilter["filters[timeRange][EQUALS]"]
        if (timeRange) {
            const end = new Date()
            const start = new Date(end.getTime() - moment.duration(timeRange).asMilliseconds())
            queryFilter["filters[startDate][GREATER_THAN_OR_EQUAL_TO]"] = start.toISOString()
            queryFilter["filters[endDate][LESS_THAN_OR_EQUAL_TO]"] = end.toISOString()
            delete queryFilter["filters[timeRange][EQUALS]"]
        }

        return _merge(base, queryFilter)
    }

    const loadData = async ({page, size, sort}: {page: number; size: number; sort?: string}) => {
        if (!loadInit.value) return

        const previousSelection = selection.value
        const query = loadQuery({
            size,
            page,
            sort: sort ?? String(route.query?.sort ?? "triggerId:asc"),
        })

        const triggersData = await triggerStore.search(query)
        triggers.value = triggersData?.results ?? []
        total.value = triggersData?.total ?? 0

        if (previousSelection?.length) {
            await dataTable.value?.waitTableRender()
            dataTable.value?.setSelection(previousSelection)
        }
    }

    const urlPage = computed(() => Number(route.query.page) || 1)
    const urlSize = computed(() => Number(route.query.size) || 25)

    const filterQueryKey = computed(() => {
        const {page: _p, size: _s, sort: _so, logsPage: _lp, logsSize: _ls, ...filters} = route.query
        return JSON.stringify(filters)
    })

    watch(filterQueryKey, () => {
        dataTable.value?.resetAndReload()
    })

    const refresh = () => dataTable.value?.reload()

    const setBackfillModal = (trigger: any, bool: boolean) => {
        if (!trigger) {
            isBackfillOpen.value = false
            selectedTrigger.value = undefined
            return
        }

        executionsStore.loadFlowForExecution({
            namespace: trigger.namespace,
            flowId: trigger.flowId,
            store: true,
        }).then(() => {
            isBackfillOpen.value = bool
            selectedTrigger.value = trigger
        })
    }

    const cleanBackfill = computed(() => {
        const labels = backfill.value.labels?.filter((label: any) => label.key && label.value)
        return {...backfill.value, labels: labels?.length ? labels : null}
    })

    const postBackfill = () => {
        const trigger = selectedTrigger.value as any
        triggerStore.createBackfill({
            namespace: trigger.namespace,
            flowId: trigger.flowId,
            triggerId: trigger.triggerId,
            backfill: cleanBackfill.value,
        })
            .then(() => {
                toast.saved(trigger?.triggerId)
                setBackfillModal(null, false)
                backfill.value = {
                    start: null,
                    end: null,
                    inputs: null,
                    labels: [],
                }
                triggerLoadDataAfterBulkEditAction()
            })
    }

    const hasLogsContent = (row: any) => row.logs && row.logs.length > 0

    const getClasses = (arg: any) => {
        const row = arg?.row ?? arg
        return hasLogsContent(row) || row?.backfill ? "expandable" : "no-expand"
    }

    const disabledStartDate = (time: Date): boolean => {
        return new Date() < time || (backfill.value.end !== null && time > backfill.value.end)
    }

    const disabledEndDate = (time: Date): boolean => {
        return new Date() < time || (backfill.value.start !== null && backfill.value.start > time)
    }

    const triggerLoadDataAfterBulkEditAction = () => {
        dataTable.value?.reload()
        setTimeout(() => dataTable.value?.reload(), 200)
        setTimeout(() => dataTable.value?.reload(), 1000)
        setTimeout(() => dataTable.value?.reload(), 5000)
    }

    const unlock = (row: any) => {
        triggerStore.unlock({
            namespace: row.namespace,
            flowId: row.flowId,
            triggerId: row.triggerId,
        }).then(() => {
            KsMessage({
                message: t("unlock trigger.success"),
                type: "success",
            })
            dataTable.value?.reload()
        })
    }

    const restart = (row: any) => {
        triggerStore.restart({
            namespace: row.namespace,
            flowId: row.flowId,
            triggerId: row.triggerId,
        }).then(() => {
            toast.saved(row.triggerId)
            dataTable.value?.reload()
        })
    }

    const openDetails = (row: any) => {
        detailsTriggerId.value = row.triggerId
        isDetailsOpen.value = true
    }

    const pauseBackfill = (row: any) => {
        triggerStore.pauseBackfill({
            namespace: row.namespace,
            flowId: row.flowId,
            triggerId: row.triggerId,
        }).then(() => {
            toast.saved(row.triggerId)
            triggerLoadDataAfterBulkEditAction()
        })
    }

    const unpauseBackfill = (row: any) => {
        triggerStore.unpauseBackfill({
            namespace: row.namespace,
            flowId: row.flowId,
            triggerId: row.triggerId,
        }).then(() => {
            toast.saved(row.triggerId)
            triggerLoadDataAfterBulkEditAction()
        })
    }

    const deleteBackfill = (row: any) => {
        triggerStore.deleteBackfill({
            namespace: row.namespace,
            flowId: row.flowId,
            triggerId: row.triggerId,
        }).then(() => {
            toast.saved(row.triggerId)
            triggerLoadDataAfterBulkEditAction()
        })
    }

    const setDisabled = (trigger: any, value: boolean) => {
        if (trigger.codeDisabled) {
            KsMessage({
                message: t("triggerflow disabled"),
                type: "error",
                showClose: true,
                duration: 1500,
            })
            return
        }
        triggerStore.setDisabled({...trigger, disabled: !value})
            .then((updatedTrigger: any) => {
                toast.saved(updatedTrigger.triggerId)
                triggers.value = triggers.value?.map((tr: any) => {
                    const {namespace, flowId, triggerId} = tr.state ?? tr.trigger ?? {}
                    return namespace === updatedTrigger.namespace
                        && flowId === updatedTrigger.flowId
                        && triggerId === updatedTrigger.triggerId
                        ? {...tr, state: updatedTrigger}
                        : tr
                })
            })
    }

    const confirmDeleteTrigger = (trigger: TriggerDeleteOptions) => {
        toast.confirm(
            t("delete trigger confirmation", {id: trigger.id}),
            () => triggerStore.delete({
                namespace: trigger.namespace,
                flowId: trigger.flowId,
                triggerId: trigger.triggerId,
            }).then(() => {
                toast.success(t("delete trigger success", {id: trigger.id}))
                dataTable.value?.reload()
            }).catch(error => {
                toast.error(t("delete trigger error", {id: trigger.id}))
                console.error(error)
            }),
            "warning",
        )
    }

    const deleteTriggers = () => {
        genericConfirmAction(
            "bulk delete triggers",
            "deleteByQuery",
            "deleteByTriggers",
            "bulk success delete triggers",
            null,
            "WARNING: deleting triggers may lead to duplicate executions if the triggers are still active in flows",
        )
    }

    const genericConfirmAction = (toastKey: string, queryAction: string, byIdAction: string, success: string, data?: any, extraWarning?: string) => {
        let message = t(toastKey, {"count": queryBulkAction.value ? total.value : selection.value?.length}) + ". " + t("bulk action async warning")

        if (extraWarning) {
            message += "<br><br><strong>" + extraWarning + "</strong>"
        }

        toast.confirm(
            message,
            () => genericConfirmCallback(queryAction, byIdAction, success, data),
        )
    }

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
        }

        if (queryBulkAction.value) {
            const query = loadQuery({})
            const options = {...query, ...data}
            const actions = actionMap[queryAction]()
            return actions(options)
                .then((d: any) => {
                    toast.success(t(success, {count: d?.count}))
                    toggleAllUnselected()
                    triggerLoadDataAfterBulkEditAction()
                })
        } else {
            const selectionData = selection.value
            const options = {triggers: selectionData, ...data}
            const actions = actionMap[byIdAction]()
            return actions(byIdAction.includes("setDisabled") ? options : selectionData)
                .then((d: any) => {
                    toast.success(t(success, {count: d?.count}))
                    toggleAllUnselected()
                    triggerLoadDataAfterBulkEditAction()
                }).catch((e: any) => {
                    toast.error(e?.invalids?.map((exec: any) => {
                        return {message: t(exec?.message, {triggers: exec?.invalidValue})}
                    }), t(e?.message))
                })
        }
    }

    const unpauseBackfills = () => {
        genericConfirmAction("bulk unpause backfills", "unpauseBackfillByQuery", "unpauseBackfillByTriggers", "bulk success unpause backfills")
    }

    const pauseBackfills = () => {
        genericConfirmAction("bulk pause backfills", "pauseBackfillByQuery", "pauseBackfillByTriggers", "bulk success pause backfills")
    }

    const deleteBackfills = () => {
        genericConfirmAction("bulk delete backfills", "deleteBackfillByQuery", "deleteBackfillByTriggers", "bulk success delete backfills")
    }

    const unlockTriggers = () => {
        genericConfirmAction("bulk unlock", "unlockByQuery", "unlockByTriggers", "bulk success unlock")
    }

    const setDisabledTriggers = (bool: boolean) => {
        genericConfirmAction(
            `bulk disabled status.${bool}`,
            "setDisabledByQuery",
            "setDisabledByTriggers",
            `bulk success disabled status.${bool}`,
            {disabled: bool},
        )
    }

    const checkBackfill = computed(() => {
        if (!backfill.value?.start) {
            return true
        }
        if (backfill.value?.end && backfill.value.start > backfill.value.end) {
            return true
        }
        if (flowStore.flow?.inputs) {
            const requiredInputs = flowStore.flow.inputs?.map((input: any) => input?.required !== false ? input?.id : null).filter((i: any) => i !== null) || []

            if (requiredInputs.length > 0) {
                if (!backfill.value?.inputs) {
                    return true
                }
                const fillInputs = Object.keys(backfill.value.inputs).filter((i: string) => backfill.value?.inputs?.[i] !== null && backfill.value?.inputs?.[i] !== undefined)
                if (requiredInputs.sort().join(",") !== fillInputs.sort().join(",")) {
                    return true
                }
            }
        }
        if (backfill.value?.labels?.length > 0) {
            for (let label of backfill.value.labels) {
                if (((label as any)?.key && !(label as any)?.value) || (!(label as any)?.key && (label as any)?.value)) {
                    return true
                }
            }
        }
        return false
    })

</script>

<style scoped lang="scss">
    .triggers-manage {
        :deep(tr.no-expand .kel-table__expand-icon) {
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

            &.is-checked .is-text {
                color: var(--ks-content-inverse);
            }
        }

        :deep(.kel-table) a {
            color: var(--ks-content-link);
        }
    }

    .backfill-tag {
        text-transform: uppercase;
    }

    .header-with-tooltip {
        display: inline-flex;
        align-items: center;
        gap: 0.25rem;
    }

    .header-tooltip-icon {
        color: var(--ks-content-secondary);
        cursor: help;
        display: inline-flex;
        align-items: center;
    }

    .pickers {
        display: flex;
        gap: 1rem;
    }

    .small-picker {
        flex: 1;
    }

    .wide-tooltip {
        max-width: 25rem;
        white-space: normal;
        word-break: break-word;
        color: var(--ks-content-primary) !important;
    }
</style>
