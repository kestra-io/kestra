<template>
    <el-splitter
        v-if="execution"
        id="overview"
        :layout="verticalLayout ? 'vertical' : 'horizontal'"
        lazy
    >
        <el-splitter-panel :size="verticalLayout ? '50%' : '35%'">
            <div class="sidebar">
                <div class="state">
                    <Row :rows="[{icon: StateMachine, label: t('state')}]">
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
                <div class="labels">
                    <Row :rows="[{icon: LabelMultiple, label: t('labels')}]">
                        <template #action>
                            <SetLabels :execution />
                        </template>
                    </Row>
                    <Labels :labels="execution.labels || []" />
                </div>

                <el-divider />
                <div class="metadata">
                    <Row :rows="metadata" />
                </div>

                <el-divider />
                <div class="actions">
                    <Row :rows="[{icon: SortVariant, label: t('actions')}]" />
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
            </div>
        </el-splitter-panel>

        <el-splitter-panel>
            <div class="main">
                <div>Main</div>
            </div>
        </el-splitter-panel>
    </el-splitter>
    <NoData
        v-else
        id="empty"
        :text="t('execution not found', {executionId: route.params.id})"
    />
</template>

<script setup lang="ts">
    import {onMounted, computed} from "vue";

    import {useRoute} from "vue-router";
    const route = useRoute();

    import {useExecutionsStore} from "../../../stores/executions";
    const store = useExecutionsStore();

    import {useMiscStore} from "override/stores/misc";
    const isOSS = computed(() => useMiscStore().configs?.edition === "OSS");

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import {useBreakpoints, breakpointsElement} from "@vueuse/core";
    const verticalLayout = useBreakpoints(breakpointsElement).smallerOrEqual("md");

    import moment from "moment";

    import Utils from "../../../utils/utils";

    import {Status, State} from "@kestra-io/ui-libs";

    import Row from "./components/sidebar/Row.vue";
    import Labels from "./components/sidebar/Labels.vue";
    import Timeline from "./components/sidebar/Timeline.vue";

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

    import StateMachine from "vue-material-design-icons/StateMachine.vue";
    import LabelMultiple from "vue-material-design-icons/LabelMultiple.vue";
    import DotsSquare from "vue-material-design-icons/DotsSquare.vue";
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

    const emits = defineEmits(["follow"]);

    const execution = computed(() => store.execution);
    const general = computed(() => {
        if (!execution.value) return [];

        return [
            {
                icon: DotsSquare,
                label: t("namespace"),
                value: execution.value.namespace,
                to: {
                    name: "namespaces/update",
                    params: {
                        ...(route.params.tenant
                            ? {tenant: route.params.tenant}
                            : {}),
                        id: execution.value.namespace,
                        tab: "overview",
                    },
                },
            },
            {
                icon: FileTreeOutline,
                label: t("flow"),
                value: execution.value.flowId,
                to: {
                    name: "flows/update",
                    params: {
                        ...(route.params.tenant
                            ? {tenant: route.params.tenant}
                            : {}),
                        namespace: execution.value.namespace,
                        id: execution.value.flowId,
                        tab: "overview",
                    },
                },
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
                                ...(route.params.tenant
                                    ? {tenant: route.params.tenant}
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
                        to: {
                            name: "executions/update",
                            params: {
                                ...(route.params.tenant
                                    ? {tenant: route.params.tenant}
                                    : {}),
                                namespace: execution.value.namespace,
                                flowId: execution.value.flowId,
                                id: execution.value.trigger.variables.executionId,
                                tab: "overview",
                            },
                        },
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
                        to: {
                            name: "executions/update",
                            params: {
                                ...(route.params.tenant
                                    ? {tenant: route.params.tenant}
                                    : {}),
                                namespace: execution.value.namespace,
                                flowId: execution.value.flowId,
                                id: execution.value.originalId,
                                tab: "overview",
                            },
                        },
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

    onMounted(() => {
        if (!route.params.id) return;
        loadExecution(route.params.id as string);
    });

    defineOptions({inheritAttrs: false});
</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/variables";

$font-size-sm: $font-size-base * 0.875; // TODO: Move it into varaibles file of ui-libs

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
                    // If button is not displayed for any reason, hide the whole column
                    display: none;
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
        // TODO: Clean up if not used in the end
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
