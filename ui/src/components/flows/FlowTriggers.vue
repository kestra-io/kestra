<template>
    <KSFilter
        v-if="triggersWithType.length"
        :configuration="triggerFilter"
        :prefix="'flow-triggers'"
        :tableOptions="{
            chart: {shown: false},
            refresh: {shown: true, callback: loadData}
        }"
        :properties="{
            shown: true,
            columns: optionalColumns,
            displayColumns,
            storageKey: storageKeys.DISPLAY_TRIGGERS_COLUMNS
        }"
        @update-properties="updateDisplayColumns"
        readOnly
        :defaultScope="false"
        :defaultTimeRange="false"
    />

    <KsDataTable
        v-if="triggersWithType.length"
        ref="dataTable"
        v-bind="$attrs"
        :data="triggersWithType"
        :total="triggersWithType.length"
        :defaultSort="{prop: 'triggerId', order: 'ascending'}"
        :rowKey="(row: any) => row.id"
        :expandRowKeys="expandedRowKeys"
        :rowClassName="(arg: any) => arg.row?.backfill ? 'force-expanded' : ''"
        :selectable="canCheck"
        :selectionMapper="selectionMapper"
    >
        <template #bulk-actions>
            <KsButton @click="bulkSetDisabled(false)">{{ $t("enable") }}</KsButton>
            <KsButton @click="bulkSetDisabled(true)">{{ $t("disable") }}</KsButton>
            <KsButton @click="bulkUnlock()">{{ $t("unlock") }}</KsButton>
            <KsButton v-if="userCan(action.DELETE)" @click="bulkDelete()">{{ $t("delete triggers") }}</KsButton>
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
                <LogsWrapper class="m-3" :filters="{...props.row, triggerId: props.row.id}" purgeFilters :withCharts="false" :reloadLogs embed />
            </template>
        </KsTableColumn>
        <KsTableColumn
            prop="id"
            :label="$t('id')"
        >
            <template #default="scope">
                <code>
                    {{ scope.row.id }}
                </code>
            </template>
        </KsTableColumn>

        <KsTableColumn
            v-for="col in visibleColumns"
            :key="col.prop"
            :prop="col.prop"
            :label="col.label"
            :sortable="DATE_COLUMNS.includes(col.prop)"
            :sortOrders="DATE_COLUMNS.includes(col.prop) ? ['ascending', 'descending'] : undefined"
        >
            <template #header v-if="col.prop === 'lastTriggeredDate'">
                <KsTooltip :content="$t('last trigger date tooltip')" placement="top" effect="light">
                    <span>{{ col.label }}</span>
                </KsTooltip>
            </template>
            <template #header v-else-if="col.prop === 'nextEvaluationDate'">
                <KsTooltip :content="$t('next evaluation date tooltip')" placement="top" effect="light">
                    <span>{{ col.label }}</span>
                </KsTooltip>
            </template>
            <template #header v-else-if="col.prop === 'updatedAt'">
                <KsTooltip :content="$t('context updated date tooltip')" placement="top" effect="light">
                    <span>{{ col.label }}</span>
                </KsTooltip>
            </template>

            <template #default="scope">
                <template v-if="col.prop === 'lastTriggeredDate'">
                    <KsDateAgo :inverted="true" :date="scope.row.lastTriggeredDate" />
                </template>
                <template v-else-if="col.prop === 'nextEvaluationDate'">
                    <KsDateAgo :inverted="true" :date="scope.row.nextEvaluationDate" />
                </template>
                <template v-else-if="col.prop === 'evaluatedAt'">
                    <KsDateAgo :inverted="true" :date="scope.row.evaluatedAt" />
                </template>
                <template v-else-if="col.prop === 'updatedAt'">
                    <KsDateAgo :inverted="true" :date="scope.row.updatedAt" />
                </template>
                <template v-else>
                    {{ scope.row[col.prop] }}
                </template>
            </template>
        </KsTableColumn>

        <KsTableColumn columnKey="backfill" :label="$t('backfill')" v-if="userCan(action.UPDATE)">
            <template #default="scope">
                <template v-if="isSchedule(scope.row.type) && !scope.row.backfill">
                    <KsButton
                        :icon="CalendarCollapseHorizontalOutline"
                        @click="setBackfillModal(scope.row, true)"
                        :disabled="scope.row.disabled || scope.row.sourceDisabled"
                        size="small"
                        type="primary"
                    >
                        {{ $t("backfill executions") }}
                    </KsButton>
                </template>
                <template v-else-if="scope.row.backfill">
                    <KsTag
                        size="small"
                        :type="scope.row.backfill.paused ? 'warning' : 'info'"
                        effect="light"
                        class="backfill-tag"
                    >
                        {{ scope.row.backfill.paused ? $t("paused") : $t("running") }}
                    </KsTag>
                </template>
            </template>
        </KsTableColumn>

        <KsTableColumn columnKey="disable" :label="$t('enabled')" className="row-action" v-if="userCan(action.UPDATE)">
            <template #default="scope">
                <KsTooltip
                    v-if="hasTrigger(scope.row)"
                    :content="$t('trigger disabled')"
                    :disabled="!scope.row.sourceDisabled"
                >
                    <KsSwitch
                        :modelValue="!(scope.row.disabled || scope.row.sourceDisabled)"
                        @change="setDisabled(scope.row, $event as boolean)"
                        inlinePrompt
                        class="switch-text"
                        :disabled="scope.row.sourceDisabled"
                    />
                </KsTooltip>
            </template>
        </KsTableColumn>

        <KsTableColumn columnKey="row-actions" className="row-action">
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
                                v-if="userCan(action.UPDATE)"
                                :disabled="!scope.row.locked"
                                @click="restart(scope.row)"
                            >
                                <Restart class="mr-1" />
                                {{ $t("restart") }}
                            </KsDropdownItem>
                            <KsDropdownItem
                                v-if="userCan(action.UPDATE)"
                                :disabled="!scope.row.locked"
                                @click="unlock(scope.row)"
                            >
                                <LockOff class="mr-1" />
                                {{ $t("unlock") }}
                            </KsDropdownItem>
                            <KsDropdownItem
                                v-if="userCan(action.DELETE)"
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

    <div v-if="triggersWithType.length" class="mt-4">
        <KsButton
            @click="addNewTrigger"
            :icon="Plus"
            class="border-0 p-3"
        >
            {{ $t('no_code.creation.triggers') }}
        </KsButton>
    </div>

    <Empty
        v-else
        type="triggers"
    >
        <template #button>
            <KsButton
                type="primary"
                @click="addNewTrigger"
                :icon="Plus"
            >
                {{ $t('no_code.creation.triggers') }}
            </KsButton>
        </template>
    </Empty>

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
                            :disabledDate="(time: Date): boolean => new Date() < time || !!(backfill.end && time > backfill.end)"
                        />
                    </KsFormItem>
                </div>
                <div class="small-picker">
                    <KsFormItem label="End">
                        <KsDatePicker
                            v-model="backfill.end"
                            type="datetime"
                            placeholder="End"
                            :disabledDate="(time: Date): boolean => new Date() < time || !!(backfill.start && backfill.start > time)"
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

    <KsDrawer
        v-if="isOpen"
        v-model="isOpen"
    >
        <template #header>
            <code>{{ triggerId }}</code>
        </template>

        <KsMarkdown v-if="triggerDefinition && (triggerDefinition as any).description" :content="(triggerDefinition as any).description" />
        <Vars :data="modalData" />
    </KsDrawer>
</template>

<script setup lang="ts">
    import {useI18n} from "vue-i18n"
    import _isEqual from "lodash/isEqual"
    import {useRoute, useRouter} from "vue-router"
    import {ref, computed, watch, onMounted, useTemplateRef} from "vue"

    import Plus from "vue-material-design-icons/Plus.vue"
    import Delete from "vue-material-design-icons/Delete.vue"
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue"
    import LockOff from "vue-material-design-icons/LockOff.vue"
    import Restart from "vue-material-design-icons/Restart.vue"
    import TextSearch from "vue-material-design-icons/TextSearch.vue"
    import CalendarCollapseHorizontalOutline from "vue-material-design-icons/CalendarCollapseHorizontalOutline.vue"

    import {KsDataTable, KsDropdown, KsDropdownMenu, KsDropdownItem, KsFilter as KSFilter, KsMarkdown, KsTag, KsTooltip} from "@kestra-io/design-system"
    //@ts-expect-error no declared types
    import FlowRun from "./FlowRun.vue"
    import Vars from "../executions/Vars.vue"
    import BackfillBanner from "./BackfillBanner.vue"
    import Empty from "../layout/empty/Empty.vue"
    import LogsWrapper from "../logs/LogsWrapper.vue"

    import action from "../../models/action"
    import resource from "../../models/resource"

    import {useToast} from "../../utils/toast"
    import {storageKeys} from "../../utils/constants"

    import {useFlowStore} from "../../stores/flow"
    import {useAuthStore} from "override/stores/auth"
    import {useTriggerStore} from "../../stores/trigger"

    import {type ColumnConfig, useTableColumns} from "../../composables/useTableColumns"
    import {useTriggerFilter} from "../filter/configurations"

    const triggerFilter = useTriggerFilter()

    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()

    defineProps<{
        embed: boolean;
    }>()

    const backfill = ref({
        start: null as Date | null,
        end: null as Date | null,
        inputs: null as any,
        labels: [] as any[],
    })
    const isOpen = ref(false)
    const triggers = ref<any[]>([])
    const isBackfillOpen = ref(false)
    const selectedTrigger = ref<any>(null)
    const triggerId = ref<string | undefined>()

    const reloadLogs = ref<number | undefined>()

    const DATE_COLUMNS: readonly string[] = ["lastTriggeredDate", "nextEvaluationDate", "evaluatedAt"]

    const optionalColumns = computed<ColumnConfig[]>(() => [
        {
            label: t("type"),
            prop: "type",
            default: true,
        },
        {
            label: t("last trigger date"),
            prop: "lastTriggeredDate",
            default: true,
        },
        {
            label: t("next evaluation date"),
            prop: "nextEvaluationDate",
            default: true,
        },
        {
            label: t("last evaluation date"),
            prop: "evaluatedAt",
            default: true,
        },
        {
            label: t("state updated date"),
            prop: "updatedAt",
            default: false,
        },
    ])

    const {
        visibleColumns: displayColumns,
        updateVisibleColumns,
    } = useTableColumns({
        columns: optionalColumns.value,
        storageKey: storageKeys.DISPLAY_TRIGGERS_COLUMNS,
        initialVisibleColumns: optionalColumns.value.filter(col => col.default).map(col => col.prop),
    })

    const visibleColumns = computed(() =>
        displayColumns.value
            .map((prop: string) => optionalColumns.value.find(c => c.prop === prop))
            .filter(Boolean) as ColumnConfig[],
    )

    const updateDisplayColumns = (newColumns: string[]) => updateVisibleColumns(newColumns)

    const expandedRowKeys = computed(() =>
        triggersWithType.value
            .filter((row: any) => !!row.backfill)
            .map((row: any) => row.id),
    )

    const toast = useToast()
    const authStore = useAuthStore()
    const flowStore = useFlowStore()
    const triggerStore = useTriggerStore()

    const query = computed(() => {
        return Array.isArray(route.query?.["filters[q][EQUALS]"]) ? route.query["filters[q][EQUALS]"][0] : route.query?.["filters[q][EQUALS]"]
    })

    const modalData = computed(() => {
        const filtered = triggersWithType.value.filter((trigger: any) => trigger?.triggerId === triggerId.value)
        if (!filtered.length) return {}
        return Object
            .entries(filtered[0])
            .filter(([key]) => !["tenantId", "namespace", "flowId", "flowRevision", "triggerId", "description"].includes(key))
            .reduce(
                (map, currentValue) => {
                    map[currentValue[0]] = currentValue[1]
                    return map
                },
                {} as any,
            )
    })

    const triggerDefinition = computed(() => {
        if (!flowStore.flow?.triggers) return undefined
        return flowStore.flow.triggers.find((trigger: any) => trigger.id === triggerId.value)
    })

    const triggersWithType = computed(() => {
        if(!flowStore.flow?.triggers) return []

        let flowTriggers = flowStore.flow?.triggers.map((trigger: any) => {
            return {...trigger, sourceDisabled: (trigger as any).disabled ?? false}
        })
        if (flowTriggers) {
            const trigs = flowTriggers.map((flowTrigger: any) => {
                let pollingTrigger = triggers.value.find((trigger: any) => trigger.triggerId === flowTrigger.id)
                return {...flowTrigger, ...pollingTrigger}
            })

            return !query.value ? trigs : trigs.filter((trigger: any) => trigger?.id?.includes(query.value))
        }
        return triggers.value
    })

    const cleanBackfill = computed(() => {
        const labels = backfill.value.labels?.filter((label: any) => label.key && label.value)
        return {...backfill.value, labels: labels?.length ? labels : null}
    })

    const checkBackfill = computed(() => {
        if (!backfill.value.start) {
            return true
        }
        if (backfill.value.end && backfill.value.start > backfill.value.end) {
            return true
        }
        if (flowStore.flow?.inputs) {
            const requiredInputs = flowStore.flow?.inputs.map((input: any) => input.required !== false ? input.id : null).filter((i: any) => i !== null)

            if (requiredInputs.length > 0) {
                if (!backfill.value.inputs) {
                    return true
                }
                const fillInputs = Object.keys(backfill.value.inputs).filter((i: string) => backfill.value.inputs[i] !== null && backfill.value.inputs[i] !== undefined)
                if (requiredInputs.sort().join(",") !== fillInputs.sort().join(",")) {
                    return true
                }
            }
        }
        if (backfill.value.labels?.length > 0) {
            for (let label of backfill.value.labels) {
                if ((label.key && !label.value) || (!label.key && label.value)) {
                    return true
                }
            }
        }
        return false
    })

    const userCan = (act: any) => {
        if (!flowStore.flow) return false
        return authStore.user?.isAllowed(resource.EXECUTION, act ? act : action.VIEW, flowStore.flow?.namespace)
    }

    const loadData = () => {
        if(!triggersWithType.value.length || !flowStore.flow) return

        triggerStore
            .find({namespace: flowStore.flow?.namespace, flowId: flowStore.flow?.id, size: triggersWithType.value.length, q: query.value})
            .then((trigs: any) => triggers.value = trigs.results)
            .then(() => reloadLogs.value = Math.random())
    }

    const setBackfillModal = (trigger: any, bool: boolean) => {
        isBackfillOpen.value = bool
        selectedTrigger.value = trigger
    }

    const loadDataAfterAction = () => loadData()

    const postBackfill = () => {
        const trigger = selectedTrigger.value as any
        triggerStore.createBackfill({
            namespace: trigger.namespace,
            flowId: trigger.flowId,
            triggerId: trigger.triggerId,
            backfill: cleanBackfill.value,
        })
            .then(() => {
                toast.saved(selectedTrigger.value?.triggerId)
                setBackfillModal(null, false)
                backfill.value = {
                    start: null,
                    end: null,
                    inputs: null,
                    labels: [],
                }
                loadDataAfterAction()
            })
    }

    const pauseBackfill = (trigger: any) => {
        triggerStore.pauseBackfill(trigger)
            .then(() => {
                toast.saved(trigger.triggerId)
                loadDataAfterAction()
            })
    }

    const unpauseBackfill = (trigger: any) => {
        triggerStore.unpauseBackfill(trigger)
            .then(() => {
                toast.saved(trigger.triggerId)
                loadDataAfterAction()
            })
    }

    const deleteBackfill = (trigger: any) => {
        triggerStore.deleteBackfill(trigger)
            .then(() => {
                toast.saved(trigger.triggerId)
                loadDataAfterAction()
            })
    }

    const setDisabled = (trigger: any, value: boolean) => {
        triggerStore.setDisabled({...trigger, disabled: !value})
            .then(() => {
                toast.saved(trigger.triggerId)
                loadDataAfterAction()
            })
    }

    const unlock = (trigger: any) => {
        triggerStore.unlock({
            namespace: trigger.namespace,
            flowId: trigger.flowId,
            triggerId: trigger.triggerId,
        }).then(() => {
            toast.saved(trigger.triggerId)
            loadDataAfterAction()
        })
    }

    const restart = (trigger: any) => {
        triggerStore.restart({
            namespace: trigger.namespace,
            flowId: trigger.flowId,
            triggerId: trigger.triggerId,
        }).then(() => {
            toast.saved(trigger.triggerId)
            loadDataAfterAction()
        })
    }

    const openDetails = (row: any) => {
        triggerId.value = row.id
        isOpen.value = true
    }

    const dataTable = useTemplateRef<any>("dataTable")
    const canCheck = computed<boolean>(() => userCan(action.UPDATE) ?? false)
    const selection = computed<any[]>(() => dataTable.value?.selection ?? [])
    const selectionMapper = (row: any) => ({
        namespace: row.namespace,
        flowId: row.flowId,
        triggerId: row.triggerId ?? row.id,
    })

    const runBulk = (promiseFactory: () => Promise<any>, successKey: string, actionLabel: string) => {
        return promiseFactory()
            .then((d: any) => {
                toast.success(t(successKey, {count: d?.count}))
                dataTable.value?.toggleAllUnselected()
                loadDataAfterAction()
            })
            .catch((error: unknown) => {
                toast.error(`${actionLabel}: ${(error as any)?.message ?? t("error")}`)
                console.error(error)
            })
    }

    const bulkSetDisabled = (disabled: boolean) => {
        const confirmKey = disabled ? "bulk disabled status.true" : "bulk disabled status.false"
        const successKey = disabled ? "bulk success disabled status.true" : "bulk success disabled status.false"
        const actionLabel = disabled ? t("disable") : t("enable")
        toast.confirm(
            t(confirmKey, {count: selection.value.length}),
            () => runBulk(
                () => triggerStore.setDisabledByTriggers({triggers: selection.value, disabled}),
                successKey,
                actionLabel,
            ),
        )
    }

    const bulkUnlock = () => {
        toast.confirm(
            t("bulk unlock", {count: selection.value.length}),
            () => runBulk(
                () => triggerStore.unlockByTriggers(selection.value),
                "bulk success unlock",
                t("unlock"),
            ),
        )
    }

    const bulkDelete = () => {
        toast.confirm(
            t("bulk delete triggers", {count: selection.value.length}),
            () => runBulk(
                () => triggerStore.deleteByTriggers(selection.value),
                "bulk success delete triggers",
                t("delete triggers"),
            ),
            "warning",
        )
    }

    const confirmDeleteTrigger = (row: any) => {
        toast.confirm(
            t("delete trigger confirmation", {id: row.id}),
            () => triggerStore.delete({
                namespace: row.namespace,
                flowId: row.flowId,
                triggerId: row.triggerId ?? row.id,
            }).then(() => {
                toast.success(t("delete trigger success", {id: row.id}))
                loadDataAfterAction()
            }).catch((error: unknown) => {
                toast.error(t("delete trigger error", {id: row.id}))
                console.error(error)
            }),
            "warning",
        )
    }

    const isSchedule = (type: string) => {
        return type === "io.kestra.plugin.core.trigger.Schedule"
    }

    const hasTrigger = (trigger: any) => {
        return triggers.value.map((trigg: any) => trigg?.triggerId).includes(trigger?.id)
    }

    const addNewTrigger = () => {
        if (!flowStore.flow) return
        router.push({
            name: "flows/update",
            params: {
                tenant: route.params?.tenant,
                namespace: flowStore.flow?.namespace,
                id: flowStore.flow?.id,
                tab: "edit",
            },
            query: {
                createTrigger: "true",
            },
        })
    }

    onMounted(() => {
        loadData()
    })

    watch(route, (newValue, oldValue) => {
        if (oldValue.name === newValue.name && !_isEqual(newValue.query, oldValue.query)) {
            loadData()
        }
    })
</script>

<style lang="scss" scoped>
.pickers {
    display: flex;
    justify-content: space-between;

    .small-picker {
        width: 49%;
    }
}

.backfill-tag {
    text-transform: uppercase;
}

:deep(tr.force-expanded .kel-table__expand-icon) {
    visibility: hidden;
    pointer-events: none;
}

:deep(.markdown) {
    p {
        margin-bottom: auto;
    }
}
</style>