<template>
    <el-splitter
        v-if="execution"
        id="overview"
        :layout="verticalLayout ? 'vertical' : 'horizontal'"
        lazy
    >
        <el-splitter-panel :size="verticalLayout ? '50%' : '30%'">
            <div class="sidebar">
                <div class="state">
                    <Row :rows="[{icon: StateMachine, label: $t('state')}]">
                        <template #action>
                            <ChangeExecutionStatus
                                :execution
                                @follow="emits('follow', $event)"
                            />
                        </template>
                    </Row>
                    <Status :status="execution.state.current" />
                    <Timeline :histories="execution.state.histories || []" />
                </div>

                <el-divider />
                <div class="general">
                    <Row :rows="general" />
                </div>

                <el-divider />
                <div class="actions">
                    <Row
                        :rows="[{icon: SortVariant, label: $t('actions')}]"
                    />
                    <el-row :gutter="12">
                        <el-col
                            v-for="(action, aIdx) in actions"
                            :key="aIdx"
                            :span="12"
                        >
                            <component
                                :is="action.component"
                                v-bind="action.props || {}"
                                v-on="action.on || {}"
                                :execution
                            />
                        </el-col>
                    </el-row>
                </div>

                <el-divider />
                <div class="metadata">
                    <Row :rows="metadata" />
                </div>

                <el-divider />
                <div class="labels">
                    <Row :rows="[{icon: LabelMultiple, label: $t('labels')}]">
                        <template #action>
                            <SetLabels :execution />
                        </template>
                    </Row>
                    <Labels :labels="execution.labels || []" />
                </div>
            </div>
        </el-splitter-panel>

        <el-splitter-panel>
            <div class="main">
                <div id="alerts">
                    <el-alert
                        v-if="matchesStatus('replayed')"
                        :title="$t('execution replayed')"
                        :closable="false"
                    />

                    <el-alert v-if="matchesStatus('replay')" :closable="false">
                        <template #title>
                            <div>
                                {{ $t("execution replay") }}
                                <router-link
                                    :to="
                                        createLink(
                                            'executions',
                                            execution,
                                            execution.originalId,
                                        )
                                    "
                                >
                                    <Id
                                        :value="execution.originalId"
                                        :shrink="false"
                                    />
                                </router-link>.
                            </div>
                        </template>
                    </el-alert>

                    <el-alert
                        v-if="matchesStatus('restarted')"
                        :title="
                            $t('execution restarted', {
                                nbRestart:
                                    execution.metadata?.attemptNumber - 1,
                            })
                        "
                        type="warning"
                        showIcon
                        :closable="false"
                    />

                    <ErrorAlert
                        v-if="execution.state.current === State.FAILED"
                        :execution
                    />
                </div>

                <Cascader
                    v-for="(cascader, cIdx) in cascaders"
                    :key="cIdx"
                    v-bind="cascader"
                    :execution
                />

                <div id="chart">
                    <div>
                        <section>
                            <div class="heading">
                                <PlayOutline />
                                <span>{{ $t("recent_executions") }}</span>
                            </div>
                            <div class="timerange">
                                <el-select
                                    v-model="timerange"
                                    @change="chartRef!.refresh(filters)"
                                >
                                    <el-option
                                        v-for="option in options"
                                        :key="option.value"
                                        :label="option.label"
                                        :value="option.value"
                                    />
                                </el-select>
                            </div>
                        </section>
                        <TimeSeries
                            ref="chartRef"
                            :chart
                            :filters
                            showDefault
                            execution
                        />
                    </div>
                </div>

                <PrevNext :execution />
            </div>
        </el-splitter-panel>
    </el-splitter>
    <NoData
        v-else
        id="empty"
        :text="$t('execution not found', {executionId: route.params.id})"
    />
</template>

<script setup lang="ts">
    import {onMounted, computed, ref} from "vue";

    import {useRoute} from "vue-router";
    const route = useRoute();

    import {useExecutionsStore} from "../../../stores/executions";
    const store = useExecutionsStore();

    import {useMiscStore} from "override/stores/misc";
    const isOSS = computed(() => useMiscStore().configs?.edition === "OSS");

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import moment from "moment";

    import {verticalLayout} from "./utils/layout";
    import {createLink} from "./utils/links";
    import Utils from "../../../utils/utils";
    import {FilterObject} from "../../../utils/filters";

    import {Status, State} from "@kestra-io/ui-libs";

    import Row from "./components/sidebar/Row.vue";
    import Labels from "./components/sidebar/Labels.vue";
    import Timeline from "./components/sidebar/Timeline.vue";

    import ErrorAlert from "./components/main/ErrorAlert.vue";
    import Id from "../../Id.vue";
    import Cascader, {type Element} from "./components/main/cascaders/Cascader.vue";
    import TimeSeries from "../../dashboard/sections/TimeSeries.vue";
    import PrevNext from "./components/main/PrevNext.vue";

    import NoData from "../../layout/NoData.vue";

    import ChangeExecutionStatus from "../ChangeExecutionStatus.vue";
    import SetLabels from "../SetLabels.vue";
    import Pause from "./components/actions/Pause.vue";
    //@ts-expect-error No declaration file
    import Resume from "./components/actions/Resume.vue";
    import Restart from "./components/actions/Restart.vue";
    import Unqueue from "./components/actions/Unqueue.vue";
    import ForceRun from "./components/actions/ForceRun.vue";
    import Kill from "./components/actions/Kill.vue";
    import Api from "./components/actions/Api.vue";
    import Delete from "./components/actions/Delete.vue";

    import yaml from "yaml";
    import YAML_CHART from "./components/main/assets/chart.yaml?raw";
    import {useValues} from "../../filter/composables/useValues";

    import StateMachine from "vue-material-design-icons/StateMachine.vue";
    import LabelMultiple from "vue-material-design-icons/LabelMultiple.vue";
    import FolderOpenOutline from "vue-material-design-icons/FolderOpenOutline.vue";
    import FileTreeOutline from "vue-material-design-icons/FileTreeOutline.vue";
    import LayersTripleOutline from "vue-material-design-icons/LayersTripleOutline.vue";
    import AccountOutline from "vue-material-design-icons/AccountOutline.vue";
    import LightningBolt from "vue-material-design-icons/LightningBolt.vue";
    import CalendarMonth from "vue-material-design-icons/CalendarMonth.vue";
    import CalendarClock from "vue-material-design-icons/CalendarClock.vue";
    import Update from "vue-material-design-icons/Update.vue";
    import TimerSand from "vue-material-design-icons/TimerSand.vue";
    import History from "vue-material-design-icons/History.vue";
    import SortVariant from "vue-material-design-icons/SortVariant.vue";
    import PlayOutline from "vue-material-design-icons/PlayOutline.vue";

    const emits = defineEmits(["follow"]);

    const execution = computed(() => store.execution);
    const general = computed(() => {
        if (!execution.value) return [];

        return [
            {
                icon: FolderOpenOutline,
                label: t("namespace"),
                value: execution.value.namespace,
                to: createLink("namespaces", execution.value),
            },
            {
                icon: FileTreeOutline,
                label: t("flow"),
                value: execution.value.flowId,
                to: createLink("flows", execution.value),
            },
            {
                icon: LayersTripleOutline,
                label: t("revision"),
                value: execution.value.flowRevision,
            },
        ];
    });
    const metadata = computed(() => {
        if (!execution.value) return [];

        return [
            ...(execution.value.trigger?.id
                ? [
                    {
                        icon: LightningBolt,
                        label: t("trigger"),
                        value: execution.value.trigger.id,
                        to: {
                            name: "admin/triggers",
                            params: {
                                ...(execution.value.tenantId
                                    ? {tenant: execution.value.tenantId}
                                    : {}),
                            },
                            query: {
                                "filters[q][EQUALS]": execution.value.trigger.id,
                            },
                        },
                    },
                ]
                : []),
            {
                icon: CalendarMonth,
                label: t("created date"),
                value: moment(execution.value.state.histories![0].date).fromNow(),
            },
            ...(execution.value.scheduleDate
                ? [
                    {
                        icon: CalendarClock,
                        label: t("scheduleDate"),
                        value: moment(execution.value.scheduleDate).fromNow(),
                    },
                ]
                : []),
            {
                icon: Update,
                label: t("latest_update"),
                value: moment(
                    State.isRunning(execution.value.state.current)
                        ? undefined // Defaults to current date
                        : execution.value.state.histories?.at(-1)?.date,
                ).fromNow(),
            },
            {
                icon: TimerSand,
                label: t("duration"),
                value: (() => {
                    const histories = execution.value.state.histories;

                    if (!histories || histories.length === 0) return "-";

                    const timestamp = (d: string) => new Date(d).getTime();

                    const start = timestamp(histories[0].date);
                    const last = histories[histories.length - 1];
                    const isRunning = State.isRunning(last.state);

                    const stop = isRunning ? Date.now() : timestamp(last.date);

                    const deltaSeconds = (stop - start) / 1000;

                    return Utils.humanDuration(deltaSeconds);
                })(),
            },
            {
                icon: LayersTripleOutline,
                label: t("attempt"),
                value: execution.value.metadata.attemptNumber,
            },
            ...(isOSS.value
                ? []
                : [
                    {
                        icon: AccountOutline,
                        label: t("user"),
                        value:
                            execution.value.labels?.find(
                                (label) => label.key === "system.username",
                            )?.value ?? "-",
                    },
                ]),
            ...(execution.value.trigger?.type ===
                "io.kestra.plugin.core.flow.Subflow" &&
                execution.value.trigger?.variables?.executionId
                ? [
                    {
                        icon: History,
                        label: t("parent execution"),
                        value: execution.value.trigger.variables.executionId,
                        to: createLink(
                            "executions",
                            execution.value,
                            execution.value.trigger.variables.executionId,
                        ),
                    },
                ]
                : []),
            ...(execution.value.originalId &&
                execution.value.originalId !== execution.value.id
                ? [
                    {
                        icon: History,
                        label: t("original execution"),
                        value: execution.value.originalId,
                        to: createLink(
                            "executions",
                            execution.value,
                            execution.value.originalId,
                        ),
                    },
                ]
                : []),
        ];
    });
    const actions = computed(() => {
        if (!execution.value) return [];

        const follow = (event: any) => emits("follow", event);

        return [
            {component: Restart, on: {follow}},
            {component: Restart, props: {isReplay: true}, on: {follow}},
            {component: Kill},
            execution.value.state.current !== "PAUSED"
                ? {component: Pause}
                : {component: Resume},
            {component: Unqueue},
            {component: ForceRun},
            {component: Api},
            {component: Delete},
        ];
    });

    const loadExecution = (id: string) => store.loadExecution({id});

    const matchesStatus = (type: "restarted" | "replayed" | "replay") => {
        if (!execution.value) return false;

        const key = `system.${type}`;

        return (
            execution.value?.labels?.some(
                (label) => label.key === key && String(label.value) === "true",
            ) ?? false
        );
    };

    const cascaders: Element[] = [
        {
            title: t("variables"),
            empty: t("no_variables"),
            elements: execution.value?.variables,
        },
        {
            title: t("inputs"),
            empty: t("no_inputs"),
            elements: execution.value?.inputs,
        },
        {
            title: t("flow_outputs"),
            empty: t("no_flow_outputs"),
            elements: execution.value?.outputs,
            includeDebug: "outputs",
        },
        {
            title: t("trigger"),
            empty: t("no_trigger"),
            elements: execution.value?.trigger,
            includeDebug: "trigger",
        },
    ];

    const options = useValues("executions").VALUES.RELATIVE_DATE;
    const timerange = ref<string>("PT168H"); // Default to last 7 days

    const chartRef = ref<InstanceType<typeof TimeSeries> | null>(null);
    const chart = {...yaml.parse(YAML_CHART), content: YAML_CHART};
    const filters = computed((): FilterObject[] => {
        if (!execution.value) return [];

        return [
            ...(execution.value.tenantId
                ? [
                    {
                        field: "tenant",
                        operation: "EQUALS",
                        value: execution.value.tenantId,
                    },
                ]
                : []),
            {
                field: "namespace",
                operation: "EQUALS",
                value: execution.value.namespace,
            },
            {
                field: "flowId",
                operation: "EQUALS",
                value: execution.value.flowId!,
            },
            {
                field: "timeRange",
                operation: "EQUALS",
                value: timerange.value,
            },
        ];
    });

    onMounted(() => {
        if (!route.params.id) return;
        loadExecution(route.params.id as string);
    });

    defineOptions({inheritAttrs: false});
</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/variables";

#overview {
    :deep(.el-splitter-panel:has(> .sidebar:first-child)) {
        background-color: var(--ks-background-table-row);
    }

    .sidebar > div,
    .main > div {
        padding: calc($spacer * 1.5);
    }

    .sidebar {
        height: 100%;

        & :deep(.state),
        & :deep(.labels) {
            .el-row {
                margin-bottom: calc($spacer * 1.5);
            }

            & button {
                width: 100%;
                overflow: hidden;

                span:not(i span) {
                    display: block;
                    min-width: 0;
                    white-space: nowrap;
                    overflow: hidden;
                    text-overflow: ellipsis;
                }
            }
        }

        & .actions .el-row {
            margin-top: calc($spacer * 1.5);

            & .el-col {
                &:empty {
                    display: none; // If button is not displayed for any reason, hide the whole column
                }

                & :deep(.el-button) {
                    width: 100%;
                    margin-bottom: calc($spacer / 1.5);
                    padding: $spacer;
                    font-size: $font-size-sm;
                    overflow: hidden;

                    span:not(i span) {
                        display: block;
                        min-width: 0;
                        white-space: nowrap;
                        overflow: hidden;
                        text-overflow: ellipsis;
                    }
                }
            }
        }
    }

    .main {
        > div {
            padding-bottom: 0;

            &:last-child {
                padding-bottom: calc($spacer * 1.5);
            }
        }

        #alerts {
            &:empty {
                display: none;
            }

            .el-alert {
                &:not(:first-child) {
                    margin-top: $spacer;
                }

                & :deep(.el-alert__icon) {
                    font-size: var(--el-alert-icon-size);
                    width: var(--el-alert-icon-size);
                    margin-right: calc($spacer * 1.5);
                }
            }
        }

        #chart {
            > div {
                padding: calc($spacer * 2);
                border: 1px solid var(--el-border-color-light);
                border-radius: calc($spacer / 2);
                background-color: var(--ks-background-card);

                > section:first-child {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    margin-bottom: $spacer;

                    & .heading {
                        display: flex;
                        align-items: center;
                        overflow: hidden;

                        & .material-design-icon {
                            margin-right: $spacer;
                            font-size: $font-size-xl;
                            color: var(--ks-content-link);
                        }

                        & span:not(.material-design-icon) {
                            display: block;
                            min-width: 0;
                            white-space: nowrap;
                            overflow: hidden;
                            text-overflow: ellipsis;
                        }
                    }

                    & .timerange {
                        .el-select {
                            width: calc($spacer * 10);
                        }
                    }
                }
            }
        }

        & :deep(.el-empty) {
            padding: 0;

            & .el-empty__image {
                width: calc($spacer * 8) !important;
            }

            & .el-empty__description {
                margin-top: calc($spacer / 2);
            }
        }
    }

    div.el-divider {
        margin: 0;
        padding: 0;
    }
}

#empty {
    height: 100%;
    background-color: var(--ks-background-table-row);
}
</style>
