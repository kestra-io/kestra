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
            columns: orderedColumns,
            displayColumns,
            storageKey: storageKeys.DISPLAY_TRIGGERS_COLUMNS
        }"
        @update-properties="updateDisplayColumns"
        readOnly
        :defaultScope="false"
        :defaultTimeRange="false"
    />

    <KsTable
        v-if="triggersWithType.length"
        v-bind="$attrs"
        :data="triggersWithType"
        tableLayout="auto"
        defaultExpandAll
    >
        <KsTableColumn type="expand">
            <template #default="props">
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
            v-for="column in orderedColumns.filter(col => displayColumns.includes(col.prop))"
            :key="column.prop"
            :prop="column.prop"
            :label="column.label"
        >
            <template #default="scope">
                <template v-if="column.prop === 'workerId'">
                    <KsId
                        :value="scope.row.workerId"
                        :shrink="true"
                    />
                </template>
                <template v-else-if="column.prop === 'nextEvaluationDate'">
                    <KsDateAgo :inverted="true" :date="scope.row.nextEvaluationDate" />
                </template>
                <template v-else>
                    {{ scope.row[column.prop] }}
                </template>
            </template>
        </KsTableColumn>

        <KsTableColumn columnKey="backfill" v-if="userCan(action.UPDATE) || userCan(action.CREATE)">
            <template #header>
                {{ $t("backfill") }}
            </template>
            <template #default="scope">
                <KsButton
                    :icon="CalendarCollapseHorizontalOutline"
                    v-if="isSchedule(scope.row.type) && !scope.row.backfill && userCan(action.CREATE)"
                    @click="setBackfillModal(scope.row, true)"
                    :disabled="scope.row.disabled || scope.row.sourceDisabled"
                    size="small"
                    type="primary"
                >
                    {{ $t("backfill executions") }}
                </KsButton>
                <template v-else-if="isSchedule(scope.row.type) && userCan(action.UPDATE)">
                    <div class="backfill-cell">
                        <div class="progress-cell">
                            <KsProgress
                                :percentage="backfillProgression(scope.row.backfill)"
                                :status="scope.row.backfill.paused ? 'warning' : ''"
                                :stroke-width="12"
                                :showText="!scope.row.backfill.paused"
                                :striped="!scope.row.backfill.paused"
                                stripedFlow
                            />
                        </div>
                        <template v-if="!scope.row.backfill.paused">
                            <KsIconButton size="small" :tooltip="$t('pause backfill')" @click="pauseBackfill(scope.row)">
                                <Pause />
                            </KsIconButton>
                        </template>
                        <template v-else-if="userCan(action.UPDATE)">
                            <KsIconButton size="small" :tooltip="$t('continue backfill')" @click="unpauseBackfill(scope.row)">
                                <Play />
                            </KsIconButton>

                            <KsIconButton size="small" :tooltip="$t('delete backfill')" @click="deleteBackfill(scope.row)">
                                <Delete />
                            </KsIconButton>
                        </template>
                    </div>
                </template>
            </template>
        </KsTableColumn>

        <KsTableColumn columnKey="disable" className="row-action" v-if="userCan(action.UPDATE)">
            <template #default="scope">
                <KsTooltip
                    v-if="hasTrigger(scope.row)"
                    :content="$t('trigger disabled')"
                    :disabled="!scope.row.sourceDisabled"
                >
                    <KsSwitch
                        size="small"
                        :activeText="$t('enabled')"
                        :modelValue="!(scope.row.disabled || scope.row.sourceDisabled)"
                        @change="setDisabled(scope.row, $event as boolean)"
                        class="switch-text"
                        :activeActionIcon="Check"
                        :disabled="scope.row.sourceDisabled"
                    />
                </KsTooltip>
            </template>
        </KsTableColumn>

        <KsTableColumn columnKey="restart" className="row-action" v-if="userCan(action.UPDATE)">
            <template #default="scope">
                <KsIconButton
                    v-if="scope.row.locked"
                    size="small"
                    :tooltip="$t('restart trigger.button')"
                    placement="left"
                    @click="restart(scope.row)"
                >
                    <Restart />
                </KsIconButton>
            </template>
        </KsTableColumn>

        <KsTableColumn columnKey="unlock" className="row-action" v-if="userCan(action.UPDATE)">
            <template #default="scope">
                <KsIconButton
                    v-if="scope.row.locked"
                    size="small"
                    :tooltip="$t('unlock trigger.button')"
                    placement="left"
                    @click="unlock(scope.row)"
                >
                    <LockOff />
                </KsIconButton>
            </template>
        </KsTableColumn>

        <KsTableColumn>
            <template #default="scope">
                <TriggerAvatar :flow="flowStore.flow" :triggerId="scope.row.id" />
            </template>
        </KsTableColumn>

        <KsTableColumn columnKey="action" className="row-action">
            <template #default="scope">
                <KsIconButton size="small" :tooltip="$t('details')" @click="triggerId = scope.row.id; isOpen = true">
                    <TextSearch />
                </KsIconButton>
            </template>
        </KsTableColumn>
    </KsTable>

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
                class="mt-3"
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
    import moment from "moment"
    import {useI18n} from "vue-i18n"
    import _isEqual from "lodash/isEqual"
    import {useRoute, useRouter} from "vue-router"
    import {ref, computed, watch, onMounted} from "vue"

    import Play from "vue-material-design-icons/Play.vue"
    import Plus from "vue-material-design-icons/Plus.vue"
    import Pause from "vue-material-design-icons/Pause.vue"
    import Check from "vue-material-design-icons/Check.vue"
    import Delete from "vue-material-design-icons/Delete.vue"
    import LockOff from "vue-material-design-icons/LockOff.vue"
    import Restart from "vue-material-design-icons/Restart.vue"
    import TextSearch from "vue-material-design-icons/TextSearch.vue"
    import CalendarCollapseHorizontalOutline from "vue-material-design-icons/CalendarCollapseHorizontalOutline.vue"

    import {KsId, KsIconButton} from "@kestra-io/design-system"
    //@ts-expect-error no declared types
    import FlowRun from "./FlowRun.vue"
    import Vars from "../executions/Vars.vue"
    import {KsMarkdown} from "@kestra-io/design-system"
    import Empty from "../layout/empty/Empty.vue"
    import TriggerAvatar from "./TriggerAvatar.vue"
    import LogsWrapper from "../logs/LogsWrapper.vue"
    import {KsFilter as KSFilter} from "@kestra-io/design-system"

    import action from "../../models/action"
    import resource from "../../models/resource"

    import {useToast} from "../../utils/toast"
    import {storageKeys} from "../../utils/constants"

    import {useFlowStore} from "../../stores/flow"
    import {useAuthStore} from "override/stores/auth"
    import {useTriggerStore} from "../../stores/trigger"

    import {useTableColumns} from "../../composables/useTableColumns"
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

    const localOptionalColumns = ref([
        {
            label: t("type"),
            prop: "type",
            default: true,
            description: t("filter.table_column.flow_triggers.type"),
        },
        {
            label: t("workerId"),
            prop: "workerId",
            default: false,
            description: t("filter.table_column.flow_triggers.workerId"),
        },
        {
            label: t("next evaluation date"),
            prop: "nextEvaluationDate",
            default: true,
            description: t("filter.table_column.flow_triggers.next execution date"),
        },
    ])

    const {
        orderedColumns,
        visibleColumns: displayColumns,
        updateVisibleColumns: updateDisplayColumns,
    } = useTableColumns({
        columns: localOptionalColumns.value,
        storageKey: storageKeys.DISPLAY_TRIGGERS_COLUMNS,
    })

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

    const backfillProgression = (backfillObj: any) => {
        const startMoment = moment(backfillObj?.start)
        const endMoment = moment(backfillObj?.end)
        const currentMoment = moment(backfillObj?.currentDate)

        const totalDuration = endMoment.diff(startMoment)
        const elapsedDuration = currentMoment.diff(startMoment)
        return Math.round((elapsedDuration / totalDuration) * 100)
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

.backfill-cell {
    display: flex;
    align-items: center;
}

.progress-cell {
    width: 200px;
    margin-right: 1em;
}

:deep(.markdown) {
    p {
        margin-bottom: auto;
    }
}
</style>