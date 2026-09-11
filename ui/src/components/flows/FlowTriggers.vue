<template>
    <div class="triggers-tab">
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
            :rowKey="(row: TriggerRow) => row.id"
            :expandRowKeys="expandedRowKeys"
            :rowClassName="(arg: {row: TriggerRow}) => arg.row.backfill ? 'force-expanded' : ''"
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
                :minWidth="140"
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
                :minWidth="col.minWidth"
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
                    <template v-else-if="col.prop === 'executionId'">
                        <router-link
                            v-if="scope.row.executionId && scope.row.namespace && scope.row.flowId"
                            :to="{name: 'executions/update', params: {tenant: route.params?.tenant, namespace: scope.row.namespace, flowId: scope.row.flowId, id: scope.row.executionId}}"
                        >
                            <KsId :value="scope.row.executionId" :shrink="true" />
                        </router-link>
                        <span v-else />
                    </template>
                    <template v-else>
                        {{ scope.row[col.prop] }}
                    </template>
                </template>
            </KsTableColumn>

            <KsTableColumn columnKey="backfill" :label="$t('backfill')" v-if="userCan(action.BACKFILL)">
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

            <KsTableColumn columnKey="disable" :label="$t('enabled')" className="row-action" v-if="userCan(action.DISABLE)">
                <template #default="scope">
                    <KsTooltip
                        v-if="hasTrigger(scope.row)"
                        :content="notToggleableReason(scope.row)"
                        :disabled="!notToggleableReason(scope.row)"
                    >
                        <!-- update:modelValue (not change) keeps the switch prop-controlled: the knob only
                             moves when the row data changes, so cancelling the enable dialog leaves it intact. -->
                        <KsSwitch
                            :modelValue="!(scope.row.disabled || scope.row.sourceDisabled)"
                            @update:modelValue="(value: string | number | boolean | undefined) => setDisabled(scope.row, Boolean(value))"
                            inlinePrompt
                            class="switch-text"
                            :disabled="!!notToggleableReason(scope.row)"
                        />
                    </KsTooltip>
                </template>
            </KsTableColumn>

            <KsTableColumn columnKey="row-actions" className="row-action" fixed="right">
                <template #default="scope">
                    <div class="row-actions-cell">
                        <KsTooltip v-if="canSendTestEvent(scope.row)" :content="$t('test_event.button')">
                            <KsButton
                                data-onboarding-target="trigger-test-event-button"
                                link
                                size="small"
                                :icon="FlashOutline"
                                :aria-label="$t('test_event.button')"
                                @click="sendTestEvent(scope.row)"
                            />
                        </KsTooltip>
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
                                        v-if="userCan(action.RESTART)"
                                        :disabled="!scope.row.locked"
                                        @click="restart(scope.row)"
                                    >
                                        <Restart class="mr-1" />
                                        {{ $t("restart") }}
                                    </KsDropdownItem>
                                    <KsDropdownItem
                                        v-if="userCan(action.UNLOCK) && scope.row.kind !== 'REALTIME'"
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
                    </div>
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
    </div>

    <TestEventDialog v-model="isTestEventOpen" :target="testEventTarget" @sent="onTestEventSent" />

    <KsDialog
        v-model="isBackfillOpen"
        destroyOnClose
        :appendToBody="true"
        :dirty="isBackfillDirty"
        scrollable
        large
    >
        <template #header>
            <span v-html="$t('backfill executions')" />
        </template>
        <KsForm :model="backfill" labelPosition="top">
            <div class="pickers">
                <div class="small-picker">
                    <KsFormItem :label="$t('start date')">
                        <KsDatePicker
                            v-model="backfill.start"
                            type="datetime"
                            :placeholder="$t('start date')"
                            :disabledDate="(time: Date): boolean => new Date() < time || !!(backfill.end && time > backfill.end)"
                        />
                    </KsFormItem>
                </div>
                <div class="small-picker">
                    <KsFormItem :label="$t('end date')">
                        <KsDatePicker
                            v-model="backfill.end"
                            type="datetime"
                            :placeholder="$t('end date')"
                            :disabledDate="(time: Date): boolean => new Date() < time || !!(backfill.start && backfill.start > time)"
                        />
                    </KsFormItem>
                </div>
            </div>
        </KsForm>
        <FlowRun
            @update-inputs="backfill.inputs = $event"
            @update-inputs-no-default="backfillInputsNoDefault = $event"
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

    <TriggerEnableDialog
        v-model="isEnableDialogOpen"
        :count="enableDialogTrigger ? undefined : selection.length"
        @confirm="onEnableDialogConfirm"
    />

    <KsDrawer
        v-if="isOpen"
        v-model="isOpen"
    >
        <template #header>
            <code>{{ triggerId }}</code>
        </template>

        <KsMarkdown v-if="triggerDefinition?.description" :content="triggerDefinition.description" />
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
    import FlashOutline from "vue-material-design-icons/FlashOutline.vue"
    import CalendarCollapseHorizontalOutline from "vue-material-design-icons/CalendarCollapseHorizontalOutline.vue"

    import {KsDataTable, KsDropdown, KsDropdownMenu, KsDropdownItem, KsFilter as KSFilter, KsMarkdown, KsTag, KsTooltip} from "@kestra-io/design-system"
    import FlowRun from "./FlowRun.vue"
    import Vars from "../executions/Vars.vue"
    import BackfillBanner from "./BackfillBanner.vue"
    import Empty from "../layout/empty/Empty.vue"
    import LogsWrapper from "../logs/LogsWrapper.vue"
    import TriggerEnableDialog from "../triggers/TriggerEnableDialog.vue"

    import action from "../../models/action"
    import resource from "../../models/resource"

    import {useToast} from "../../utils/toast"
    import {storageKeys} from "../../utils/constants"

    import {useFlowStore} from "../../stores/flow"
    import {useAuthStore} from "override/stores/auth"
    import * as TriggersAPI from "@kestra-io/kestra-sdk/triggers"
    import type {
        AbstractTrigger,
        ApiAsyncOperationResponse,
        ApiTriggerState,
        Label,
        TriggerControllerApiCreateBackfillRequest,
        TriggerControllerApiTriggerId,
    } from "@kestra-io/kestra-sdk"
    import {searchTriggersForFlow} from "../../utils/triggers"
    import {useProductTourStore} from "../../stores/productTour"
    import TestEventDialog, {type TestEventTarget} from "./TestEventDialog.vue"
    import {WEBHOOK_TRIGGER_TYPE} from "../../utils/webhook"

    import {useTableColumns, type ColumnConfig} from "@kestra-io/design-system"
    import {useTriggerFilter} from "../filter/configurations"

    const triggerFilter = useTriggerFilter()

    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()

    defineProps<{
        embed: boolean;
    }>()

    // A row is the flow's trigger definition merged with its scheduler state, which is absent until
    // the trigger has been evaluated; `inputs` (Schedule) and `key` (Webhook) come from trigger
    // plugin subclasses, which AbstractTrigger does not model.
    type TriggerRow = AbstractTrigger & Partial<ApiTriggerState> & {
        sourceDisabled: boolean;
        inputs?: Record<string, unknown>;
        key?: string;
    }

    /** The part of the KsDataTable instance this table drives. */
    interface TriggersDataTable {
        selection: TriggerControllerApiTriggerId[];
        toggleAllUnselected: () => void;
    }

    const backfill = ref<{
        start: Date | null;
        end: Date | null;
        inputs: Record<string, unknown> | null;
        labels: Label[];
    }>({
        start: null,
        end: null,
        inputs: null,
        labels: [],
    })
    const isOpen = ref(false)
    const triggers = ref<ApiTriggerState[]>([])
    const isBackfillOpen = ref(false)
    const selectedTrigger = ref<TriggerRow | undefined>()

    // kept out of `backfill` so it never leaks into the submitted payload (cleanBackfill spreads backfill)
    const backfillInputsNoDefault = ref<Record<string, unknown>>({})

    const isBackfillDirty = computed(() => !!(
        backfill.value.start ||
        backfill.value.end ||
        Object.keys(backfillInputsNoDefault.value).length > 0 ||
        backfill.value.labels.some(label => label.key || label.value)
    ))
    const triggerId = ref<string | undefined>()

    const reloadLogs = ref<number | undefined>()

    const DATE_COLUMNS: readonly string[] = ["lastTriggeredDate", "nextEvaluationDate", "evaluatedAt"]

    const optionalColumns = computed<ColumnConfig[]>(() => [
        {
            label: t("type"),
            prop: "type",
            default: true,
            minWidth: 260,
        },
        {
            label: t("execution id"),
            prop: "executionId",
            default: true,
            minWidth: 140,
        },
        {
            label: t("last trigger date"),
            prop: "lastTriggeredDate",
            default: true,
            minWidth: 150,
        },
        {
            label: t("next evaluation date"),
            prop: "nextEvaluationDate",
            default: true,
            minWidth: 150,
        },
        {
            label: t("last evaluation date"),
            prop: "evaluatedAt",
            default: true,
            minWidth: 150,
        },
        {
            label: t("state updated date"),
            prop: "updatedAt",
            default: false,
            minWidth: 150,
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

    const expandedRowKeys = computed<string[]>(() =>
        triggersWithType.value
            .filter(row => !!row.backfill)
            .map(row => row.id),
    )

    const toast = useToast()
    const authStore = useAuthStore()
    const flowStore = useFlowStore()

    const query = computed(() => {
        return Array.isArray(route.query?.["filters[q][EQUALS]"]) ? route.query["filters[q][EQUALS]"][0] : route.query?.["filters[q][EQUALS]"]
    })

    const modalData = computed<Record<string, unknown>>(() => {
        const trigger = triggersWithType.value.find(row => row.triggerId === triggerId.value)
        if (!trigger) return {}
        return Object
            .entries(trigger)
            .filter(([key]) => !["tenantId", "namespace", "flowId", "flowRevision", "triggerId", "description"].includes(key))
            .reduce<Record<string, unknown>>((map, [key, value]) => {
                map[key] = value
                return map
            }, {})
    })

    const triggerDefinition = computed(() =>
        flowStore.flow?.triggers?.find(trigger => trigger.id === triggerId.value),
    )

    const triggersWithType = computed<TriggerRow[]>(() => {
        const flowTriggers = flowStore.flow?.triggers
        if (!flowTriggers) return []

        const rows = flowTriggers.map(trigger => ({
            ...trigger,
            sourceDisabled: trigger.disabled ?? false,
            ...triggers.value.find(state => state.triggerId === trigger.id),
        }))

        const search = query.value
        return search ? rows.filter(row => row.id.includes(search)) : rows
    })

    type BackfillRequest = TriggerControllerApiCreateBackfillRequest["backfill"]

    const cleanBackfill = computed<BackfillRequest>(() => {
        const labels = backfill.value.labels.filter(label => label.key && label.value)
        return {
            start: backfill.value.start?.toISOString(),
            end: backfill.value.end?.toISOString(),
            // the spec models the endpoint's Map<String, Object> inputs as a map of maps
            inputs: (backfill.value.inputs ?? undefined) as BackfillRequest["inputs"],
            labels: labels.length ? labels : undefined,
        }
    })

    const checkBackfill = computed(() => {
        const {start, end, inputs, labels} = backfill.value

        if (!start) {
            return true
        }
        if (end && start > end) {
            return true
        }
        if (flowStore.flow?.inputs) {
            const requiredInputs = flowStore.flow.inputs
                .filter(input => input.required !== false)
                .map(input => input.id)

            if (requiredInputs.length > 0) {
                if (!inputs) {
                    return true
                }
                const fillInputs = Object.keys(inputs).filter(key => inputs[key] !== null && inputs[key] !== undefined)
                if (requiredInputs.sort().join(",") !== fillInputs.sort().join(",")) {
                    return true
                }
            }
        }

        return labels.some(label => Boolean(label.key) !== Boolean(label.value))
    })

    const userCan = (act?: string) => {
        if (!flowStore.flow) return false
        return authStore.user?.isAllowed(resource.TRIGGER, act ? act : action.VIEW, flowStore.flow?.namespace)
    }

    const loadData = () => {
        const flow = flowStore.flow
        if(!triggersWithType.value.length || !flow) return

        searchTriggersForFlow({namespace: flow.namespace, flowId: flow.id, size: triggersWithType.value.length, q: query.value})
            .then(trigs => triggers.value = trigs.results)
            .then(() => reloadLogs.value = Math.random())
    }

    // The identity the trigger endpoints address a row by: a trigger with no state row yet carries
    // only its definition id, and its namespace and flow are the ones being viewed.
    const triggerIdentity = (row: TriggerRow) => ({
        namespace: row.namespace ?? flowStore.flow?.namespace ?? "",
        flowId: row.flowId ?? flowStore.flow?.id ?? "",
        triggerId: row.triggerId ?? row.id,
    })

    const setBackfillModal = (trigger: TriggerRow | null, bool: boolean) => {
        if (bool) {
            backfill.value = {start: null, end: null, inputs: null, labels: []}
            backfillInputsNoDefault.value = {}
        }
        isBackfillOpen.value = bool
        selectedTrigger.value = trigger ?? undefined
    }

    const loadDataAfterAction = () => loadData()

    const postBackfill = () => {
        const trigger = selectedTrigger.value
        if (!trigger) return

        const identity = triggerIdentity(trigger)
        TriggersAPI.createBackfill({...identity, backfill: cleanBackfill.value})
            .then(() => {
                toast.saved(identity.triggerId)
                setBackfillModal(null, false)
                backfill.value = {start: null, end: null, inputs: null, labels: []}
                loadDataAfterAction()
            })
    }

    const pauseBackfill = (trigger: TriggerRow) => {
        const identity = triggerIdentity(trigger)
        TriggersAPI.pauseBackfill(identity)
            .then(() => {
                toast.saved(identity.triggerId)
                loadDataAfterAction()
            })
    }

    const unpauseBackfill = (trigger: TriggerRow) => {
        const identity = triggerIdentity(trigger)
        TriggersAPI.unpauseBackfill(identity)
            .then(() => {
                toast.saved(identity.triggerId)
                loadDataAfterAction()
            })
    }

    const deleteBackfill = (trigger: TriggerRow) => {
        const identity = triggerIdentity(trigger)
        TriggersAPI.deleteBackfill(identity)
            .then(() => {
                toast.saved(identity.triggerId)
                loadDataAfterAction()
            })
    }

    const isEnableDialogOpen = ref(false)
    // The schedule trigger being enabled; null when enabling the bulk selection.
    const enableDialogTrigger = ref<TriggerRow | null>(null)

    const isScheduleTrigger = (row?: TriggerRow) => row?.kind === "SCHEDULE" || isSchedule(row?.type)

    // A trigger the scheduler does not evaluate never reads the stored disabled flag, so the API refuses to
    // toggle it: it can only be disabled in the flow source.
    const notToggleableReason = (row: TriggerRow): string | undefined => {
        if (row.sourceDisabled) return t("trigger disabled")
        if (row.kind === "UNSCHEDULED") return t("trigger not toggleable")
        return undefined
    }

    const setDisabled = (trigger: TriggerRow, value: boolean) => {
        if (value && isScheduleTrigger(trigger)) {
            enableDialogTrigger.value = trigger
            isEnableDialogOpen.value = true
            return
        }
        doSetDisabled(trigger, !value)
    }

    const doSetDisabled = (trigger: TriggerRow, disabled: boolean, recoverMissedSchedules?: boolean) => {
        const identity = triggerIdentity(trigger)
        TriggersAPI.disableTriggerById({...identity, disabled, recoverMissedSchedules})
            .then(() => {
                toast.saved(identity.triggerId)
                loadDataAfterAction()
            })
    }

    const onEnableDialogConfirm = (recoverMissedSchedules?: boolean) => {
        if (enableDialogTrigger.value) {
            doSetDisabled(enableDialogTrigger.value, false, recoverMissedSchedules)
        } else {
            runBulk(
                () => TriggersAPI.disabledTriggersByIds({triggers: selection.value, disabled: false, recoverMissedSchedules}),
                "bulk success disabled status.false",
                t("enable"),
            )
        }
    }

    const unlock = (trigger: TriggerRow) => {
        const identity = triggerIdentity(trigger)
        TriggersAPI.unlockTrigger(identity).then(() => {
            toast.saved(identity.triggerId)
            loadDataAfterAction()
        })
    }

    const restart = (trigger: TriggerRow) => {
        const identity = triggerIdentity(trigger)
        TriggersAPI.restartTrigger(identity).then(() => {
            toast.saved(identity.triggerId)
            loadDataAfterAction()
        })
    }

    const openDetails = (row: TriggerRow) => {
        triggerId.value = row.id
        isOpen.value = true
    }

    const tourStore = useProductTourStore()
    const testEventTarget = ref<TestEventTarget | null>(null)
    const isTestEventOpen = ref(false)

    // Gated on execution-create: a test event creates a real execution.
    const canSendTestEvent =(row: TriggerRow) =>
        row?.type === WEBHOOK_TRIGGER_TYPE
        && Boolean(row?.key)
        && Boolean(authStore.user?.isAllowed(resource.EXECUTION, action.CREATE, flowStore.flow?.namespace))

    const sendTestEvent = (row: TriggerRow) => {
        testEventTarget.value = {...triggerIdentity(row), key: row.key ?? ""}
        isTestEventOpen.value = true
    }

    const onTestEventSent =(result: {executionId?: string}) => {
        if (!tourStore.isGuidedActive || !result?.executionId) return
        tourStore.setTourState({
            eventExecutionId: result.executionId,
            lastExecutionId: result.executionId,
        })
    }

    const dataTable = useTemplateRef<TriggersDataTable>("dataTable")
    const canCheck = computed<boolean>(() => userCan(action.UPDATE) ?? false)
    const selection = computed<TriggerControllerApiTriggerId[]>(() => dataTable.value?.selection ?? [])
    const selectionMapper = (row: TriggerRow): TriggerControllerApiTriggerId => ({
        namespace: row.namespace,
        flowId: row.flowId,
        triggerId: row.triggerId ?? row.id,
    })

    const runBulk = (promiseFactory: () => Promise<ApiAsyncOperationResponse>, successKey: string, actionLabel: string) => {
        return promiseFactory()
            .then(response => {
                toast.success(t(successKey, {count: response.totalItems}))
                dataTable.value?.toggleAllUnselected()
                loadDataAfterAction()
            })
            .catch((error: unknown) => {
                toast.error(`${actionLabel}: ${error instanceof Error ? error.message : t("error")}`)
                console.error(error)
            })
    }

    const bulkSetDisabled = (disabled: boolean) => {
        if (!disabled && selection.value.some(sel => isScheduleTrigger(findRowBySelection(sel)))) {
            enableDialogTrigger.value = null
            isEnableDialogOpen.value = true
            return
        }
        const confirmKey = disabled ? "bulk disabled status.true" : "bulk disabled status.false"
        const successKey = disabled ? "bulk success disabled status.true" : "bulk success disabled status.false"
        const actionLabel = disabled ? t("disable") : t("enable")
        toast.confirm(
            t(confirmKey, {count: selection.value.length}),
            () => runBulk(
                () => TriggersAPI.disabledTriggersByIds({triggers: selection.value, disabled}),
                successKey,
                actionLabel,
            ),
        )
    }

    const findRowBySelection = (sel: TriggerControllerApiTriggerId) =>
        triggersWithType.value.find(row => (row.triggerId ?? row.id) === sel.triggerId)

    const bulkUnlock = () => {
        toast.confirm(
            t("bulk unlock", {count: selection.value.length}),
            () => runBulk(
                () => TriggersAPI.unlockTriggersByIds({body: selection.value}),
                "bulk success unlock",
                t("unlock"),
            ),
        )
    }

    const bulkDelete = () => {
        toast.confirm(
            t("bulk delete triggers", {count: selection.value.length}),
            () => runBulk(
                () => TriggersAPI.deleteTriggersByIds({body: selection.value}),
                "bulk success delete triggers",
                t("delete triggers"),
            ),
            "warning",
        )
    }

    const confirmDeleteTrigger = (row: TriggerRow) => {
        toast.confirm(
            t("delete trigger confirmation", {id: row.id}),
            () => TriggersAPI.deleteTrigger(triggerIdentity(row)).then(() => {
                toast.success(t("delete trigger success", {id: row.id}))
                loadDataAfterAction()
            }).catch((error: unknown) => {
                toast.error(t("delete trigger error", {id: row.id}))
                console.error(error)
            }),
            "warning",
        )
    }

    const isSchedule = (type?: string) => {
        return type === "io.kestra.plugin.core.trigger.Schedule"
    }

    const hasTrigger = (row: TriggerRow) => {
        return triggers.value.some(state => state.triggerId === row.id)
    }

    const addNewTrigger = () => {
        if (!flowStore.flow) return
        router.push({
            name: "flows/update/edit",
            params: {
                tenant: route.params?.tenant,
                namespace: flowStore.flow?.namespace,
                id: flowStore.flow?.id,
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
// The parent flow tab strips horizontal padding when a data table is present
// (full-width-table behaviour); restore the standard gutter for this tab.
.triggers-tab {
    padding-inline: var(--ks-spacing-5);
}

.row-actions-cell {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: var(--ks-spacing-1);
}

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