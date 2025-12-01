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
        legacyQuery
        readOnly
        :defaultScope="false"
        :defaultTimeRange="false"
    />

    <el-table
        v-if="triggersWithType.length"
        v-bind="$attrs"
        :data="triggersWithType"
        tableLayout="auto"
        defaultExpandAll
    >
        <el-table-column type="expand">
            <template #default="props">
                <LogsWrapper class="m-3" :filters="{...props.row, triggerId: props.row.id}" purgeFilters :withCharts="false" :reloadLogs embed />
            </template>
        </el-table-column>
        <el-table-column
            prop="id"
            :label="$t('id')"
        >
            <template #default="scope">
                <code>
                    {{ scope.row.id }}
                </code>
            </template>
        </el-table-column>

        <el-table-column
            v-for="column in orderedColumns.filter(col => displayColumns.includes(col.prop))"
            :key="column.prop"
            :prop="column.prop"
            :label="column.label"
        >
            <template #default="scope">
                <template v-if="column.prop === 'workerId'">
                    <Id
                        :value="scope.row.workerId"
                        :shrink="true"
                    />
                </template>
                <template v-else-if="column.prop === 'nextExecutionDate'">
                    <DateAgo :inverted="true" :date="scope.row.nextExecutionDate" />
                </template>
                <template v-else>
                    {{ scope.row[column.prop] }}
                </template>
            </template>
        </el-table-column>

        <el-table-column columnKey="backfill" v-if="userCan(action.UPDATE) || userCan(action.CREATE)">
            <template #header>
                {{ $t("backfill") }}
            </template>
            <template #default="scope">
                <el-button
                    :icon="CalendarCollapseHorizontalOutline"
                    v-if="isSchedule(scope.row.type) && !scope.row.backfill && userCan(action.CREATE)"
                    @click="setBackfillModal(scope.row, true)"
                    :disabled="scope.row.disabled || scope.row.sourceDisabled"
                    size="small"
                    type="primary"
                >
                    {{ $t("backfill executions") }}
                </el-button>
                <template v-else-if="isSchedule(scope.row.type) && userCan(action.UPDATE)">
                    <div class="backfill-cell">
                        <div class="progress-cell">
                            <el-progress
                                :percentage="backfillProgression(scope.row.backfill)"
                                :status="scope.row.backfill.paused ? 'warning' : ''"
                                :stroke-width="12"
                                :showText="!scope.row.backfill.paused"
                                :striped="!scope.row.backfill.paused"
                                stripedFlow
                            />
                        </div>
                        <template v-if="!scope.row.backfill.paused">
                            <el-button size="small" @click="pauseBackfill(scope.row)">
                                <Kicon :tooltip="$t('pause backfill')">
                                    <Pause />
                                </Kicon>
                            </el-button>
                        </template>
                        <template v-else-if="userCan(action.UPDATE)">
                            <el-button size="small" @click="unpauseBackfill(scope.row)">
                                <Kicon :tooltip="$t('continue backfill')">
                                    <Play />
                                </Kicon>
                            </el-button>

                            <el-button size="small" @click="deleteBackfill(scope.row)">
                                <Kicon :tooltip="$t('delete backfill')">
                                    <Delete />
                                </Kicon>
                            </el-button>
                        </template>
                    </div>
                </template>
            </template>
        </el-table-column>

        <el-table-column columnKey="disable" className="row-action" v-if="userCan(action.UPDATE)">
            <template #default="scope">
                <el-switch
                    v-if="canBeDisabled(scope.row)"
                    size="small"
                    :activeText="$t('enabled')"
                    :modelValue="!scope.row.disabled"
                    @change="setDisabled(scope.row, $event)"
                    class="switch-text"
                    :activeActionIcon="Check"
                />
            </template>
        </el-table-column>

        <el-table-column columnKey="restart" className="row-action" v-if="userCan(action.UPDATE)">
            <template #default="scope">
                <el-button size="small" v-if="scope.row.evaluateRunningDate" @click="restart(scope.row)">
                    <Kicon :tooltip="$t('restart trigger.button')">
                        <Restart />
                    </Kicon>
                </el-button>
            </template>
        </el-table-column>

        <el-table-column columnKey="unlock" className="row-action" v-if="userCan(action.UPDATE)">
            <template #default="scope">
                <el-button size="small" v-if="scope.row.executionId" @click="unlock(scope.row)">
                    <Kicon :tooltip="$t('unlock trigger.button')">
                        <LockOff />
                    </Kicon>
                </el-button>
            </template>
        </el-table-column>

        <el-table-column>
            <template #default="scope">
                <TriggerAvatar :flow="flowStore.flow" :triggerId="scope.row.id" />
            </template>
        </el-table-column>

        <el-table-column columnKey="action" className="row-action">
            <template #default="scope">
                <el-button size="small" @click="triggerId = scope.row.id; isOpen = true">
                    <Kicon :tooltip="$t('details')" placement="left">
                        <TextSearch />
                    </Kicon>
                </el-button>
            </template>
        </el-table-column>
    </el-table>

    <div v-if="triggersWithType.length" class="mt-4">
        <el-button
            @click="addNewTrigger"
            :icon="Plus"
            class="border-0 p-3"
        >
            {{ $t('no_code.creation.triggers') }}
        </el-button>
    </div>

    <Empty
        v-else
        type="triggers"
    >
        <template #button>
            <el-button
                type="primary"
                @click="addNewTrigger"
                :icon="Plus"
                class="mt-3"
            >
                {{ $t('no_code.creation.triggers') }}
            </el-button>
        </template>
    </Empty>

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
                            :disabledDate="(time: Date) => new Date() < time || (backfill.end && time > backfill.end)"
                        />
                    </el-form-item>
                </div>
                <div class="small-picker">
                    <el-form-item label="End">
                        <el-date-picker
                            v-model="backfill.end"
                            type="datetime"
                            placeholder="End"
                            :disabledDate="(time: Date) => new Date() < time || (backfill.start && backfill.start > time)"
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

    <Drawer
        v-if="isOpen"
        v-model="isOpen"
    >
        <template #header>
            <code>{{ triggerId }}</code>
        </template>

        <Markdown v-if="triggerDefinition && (triggerDefinition as any).description" :source="(triggerDefinition as any).description" />
        <Vars :data="modalData" />
    </Drawer>
</template>

<script setup lang="ts">
    import moment from "moment";
    import {useI18n} from "vue-i18n";
    import _isEqual from "lodash/isEqual";
    import {useRoute, useRouter} from "vue-router";
    import {ref, computed, watch, onMounted, nextTick} from "vue";

    import Play from "vue-material-design-icons/Play.vue";
    import Plus from "vue-material-design-icons/Plus.vue";
    import Pause from "vue-material-design-icons/Pause.vue";
    import Check from "vue-material-design-icons/Check.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import LockOff from "vue-material-design-icons/LockOff.vue";
    import Restart from "vue-material-design-icons/Restart.vue";
    import TextSearch from "vue-material-design-icons/TextSearch.vue";
    import CalendarCollapseHorizontalOutline from "vue-material-design-icons/CalendarCollapseHorizontalOutline.vue";

    import Id from "../Id.vue";
    import Kicon from "../Kicon.vue";
    import Drawer from "../Drawer.vue";
    //@ts-expect-error no declared types
    import FlowRun from "./FlowRun.vue";
    import Vars from "../executions/Vars.vue";
    import DateAgo from "../layout/DateAgo.vue";
    import Markdown from "../layout/Markdown.vue";
    import Empty from "../layout/empty/Empty.vue";
    import TriggerAvatar from "./TriggerAvatar.vue";
    import LogsWrapper from "../logs/LogsWrapper.vue";
    import KSFilter from "../filter/components/KSFilter.vue";

    import action from "../../models/action";
    import permission from "../../models/permission";
    
    import {useToast} from "../../utils/toast";
    import {storageKeys} from "../../utils/constants";

    import {useFlowStore} from "../../stores/flow";
    import {useAuthStore} from "override/stores/auth";
    import {useTriggerStore} from "../../stores/trigger";

    import {useTableColumns} from "../../composables/useTableColumns";
    import {useTriggerFilter} from "../filter/configurations";

    const triggerFilter = useTriggerFilter();

    const {t} = useI18n();
    const route = useRoute();
    const router = useRouter();

    defineProps<{
        embed: boolean;
    }>();

    const backfill = ref({
        start: null as Date | null,
        end: null as Date | null,
        inputs: null as any,
        labels: [] as any[]
    });
    const isOpen = ref(false);
    const triggers = ref<any[]>([]);
    const isBackfillOpen = ref(false);
    const selectedTrigger = ref<any>(null);
    const triggerId = ref<string | undefined>();

    const reloadLogs = ref<number | undefined>();

    const localOptionalColumns = ref([
        {
            label: t("type"), 
            prop: "type", 
            default: true, 
            description: t("filter.table_column.flow_triggers.type")
        },
        {
            label: t("workerId"), 
            prop: "workerId", 
            default: false, 
            description: t("filter.table_column.flow_triggers.workerId")
        },
        {
            label: t("next execution date"), 
            prop: "nextExecutionDate", 
            default: true, 
            description: t("filter.table_column.flow_triggers.next execution date")
        }
    ]);

    const {
        orderedColumns, 
        visibleColumns: displayColumns, 
        updateVisibleColumns: updateDisplayColumns
    } = useTableColumns({
        columns: localOptionalColumns.value,
        storageKey: storageKeys.DISPLAY_TRIGGERS_COLUMNS
    });

    const toast = useToast();
    const authStore = useAuthStore();
    const flowStore = useFlowStore();
    const triggerStore = useTriggerStore();

    const query = computed(() => {
        return Array.isArray(route.query?.q) ? route.query.q[0] : route.query?.q;
    });

    const modalData = computed(() => {
        const filtered = triggersWithType.value.filter((trigger: any) => trigger?.triggerId === triggerId.value);
        if (!filtered.length) return {};
        return Object
            .entries(filtered[0])
            .filter(([key]) => !["tenantId", "namespace", "flowId", "flowRevision", "triggerId", "description"].includes(key))
            .reduce(
                (map, currentValue) => {
                    map[currentValue[0]] = currentValue[1];
                    return map;
                },
                {} as any,
            );
    });

    const triggerDefinition = computed(() => {
        if (!flowStore.flow?.triggers) return undefined;
        return flowStore.flow.triggers.find((trigger: any) => trigger.id === triggerId.value);
    });

    const triggersWithType = computed(() => {
        if(!flowStore.flow?.triggers) return [];

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
    });

    const cleanBackfill = computed(() => {
        return {...backfill.value, labels: backfill.value.labels?.filter((label: any) => label.key && label.value)}
    });

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
                const fillInputs = Object.keys(backfill.value.inputs).filter((i: string) => backfill.value.inputs[i] !== null && backfill.value.inputs[i] !== undefined);
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
    });

    const editorViewType = computed(() => {
        return localStorage.getItem(storageKeys.EDITOR_VIEW_TYPE) === "NO_CODE";
    });

    const userCan = (act: any) => {
        if (!flowStore.flow) return false;
        return authStore.user?.isAllowed(permission.EXECUTION, act ? act : action.READ, flowStore.flow?.namespace);
    };

    const loadData = () => {
        if(!triggersWithType.value.length || !flowStore.flow) return;

        triggerStore
            .find({namespace: flowStore.flow?.namespace, flowId: flowStore.flow?.id, size: triggersWithType.value.length, q: query.value})
            .then((trigs: any) => triggers.value = trigs.results)
            .then(() => reloadLogs.value = Math.random());
    };

    const setBackfillModal = (trigger: any, bool: boolean) => {
        isBackfillOpen.value = bool
        selectedTrigger.value = trigger
    };

    const postBackfill = () => {
        triggerStore.update({
            ...selectedTrigger.value,
            backfill: cleanBackfill.value
        })
            .then((newTrigger: any) => {
                toast.saved(newTrigger.triggerId);
                triggers.value = triggers.value.map((t: any) => {
                    if (t.id === newTrigger.id) {
                        return newTrigger
                    }
                    return t
                })
                setBackfillModal(null, false);
                backfill.value = {
                    start: null,
                    end: null,
                    inputs: null,
                    labels: []
                }
            })

    };

    const pauseBackfill = (trigger: any) => {
        triggerStore.pauseBackfill(trigger)
            .then((newTrigger: any) => {
                toast.saved(newTrigger.triggerId);
                triggers.value = triggers.value.map((t: any) => {
                    if (t.id === newTrigger.id) {
                        return newTrigger
                    }
                    return t
                })
            })
    };

    const unpauseBackfill = (trigger: any) => {
        triggerStore.unpauseBackfill(trigger)
            .then((newTrigger: any) => {
                toast.saved(newTrigger.triggerId);
                triggers.value = triggers.value.map((t: any) => {
                    if (t.id === newTrigger.id) {
                        return newTrigger
                    }
                    return t
                })
            })
    };

    const deleteBackfill = (trigger: any) => {
        triggerStore.deleteBackfill(trigger)
            .then((newTrigger: any) => {
                toast.saved(newTrigger.triggerId);
                triggers.value = triggers.value.map((t: any) => {
                    if (t.id === newTrigger.id) {
                        return newTrigger
                    }
                    return t
                })
            })
    };

    const setDisabled = (trigger: any, value: boolean) => {
        triggerStore.update({...trigger, disabled: !value})
            .then((newTrigger: any) => {
                toast.saved(newTrigger.triggerId);
                triggers.value = triggers.value.map((t: any) => {
                    if (t.id === newTrigger.id) {
                        return newTrigger
                    }
                    return t
                })
            })
    };

    const unlock = (trigger: any) => {
        triggerStore.unlock({
            namespace: trigger.namespace,
            flowId: trigger.flowId,
            triggerId: trigger.triggerId
        }).then((newTrigger: any) => {
            toast.saved(newTrigger.triggerId);
            triggers.value = triggers.value.map((t: any) => {
                if (t.id === newTrigger.id) {
                    return newTrigger
                }
                return t
            })
        })
    };

    const restart = (trigger: any) => {
        triggerStore.restart({
            namespace: trigger.namespace,
            flowId: trigger.flowId,
            triggerId: trigger.triggerId
        }).then((newTrigger: any) => {
            toast.saved(newTrigger.triggerId);
            triggers.value = triggers.value.map((t: any) => {
                if (t.id === newTrigger.id) {
                    return newTrigger
                }
                return t
            })
        })
    };

    const backfillProgression = (backfillObj: any) => {
        const startMoment = moment(backfillObj?.start);
        const endMoment = moment(backfillObj?.end);
        const currentMoment = moment(backfillObj?.currentDate);

        const totalDuration = endMoment.diff(startMoment);
        const elapsedDuration = currentMoment.diff(startMoment);
        return Math.round((elapsedDuration / totalDuration) * 100);
    };

    const isSchedule = (type: string) => {
        return type === "io.kestra.plugin.core.trigger.Schedule" || type === "io.kestra.core.models.triggers.types.Schedule";
    };

    const canBeDisabled = (trigger: any) => {
        return triggers.value.map((trigg: any) => trigg?.triggerId).includes(trigger?.id)
            && !trigger?.sourceDisabled;
    };

    const addNewTrigger = () => {
        if (!flowStore.flow) return;
        localStorage.setItem(storageKeys.EDITOR_VIEW_TYPE, "NO_CODE");

        const baseUrl = {
            name: "flows/update",
            params: {
                tenant: route.params?.tenant,
                namespace: flowStore.flow?.namespace,
                id: flowStore.flow?.id,
                tab: "edit"
            }
        };

        if (editorViewType.value) {
            const r = {
                ...baseUrl,
                query: {
                    section: "triggers"
                }
            };

            nextTick(() => {
                router.push(r).then(() => {
                    router.replace({
                        ...r,
                        query: {
                            ...r.query,
                        }
                    });
                });
            });
        } else {
            router.push(baseUrl);
        }
    };

    onMounted(() => {
        loadData();
    });

    watch(route, (newValue, oldValue) => {
        if (oldValue.name === newValue.name && !_isEqual(newValue.query, oldValue.query)) {
            loadData();
        }
    });
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