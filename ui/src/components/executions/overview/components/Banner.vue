<template>
    <div class="execution-banner">
        <div class="execution-banner__content">
            <div class="execution-banner__state">
                <ChangeExecutionStatus :execution @follow="emit('follow', $event)">
                    <template #trigger="{visible, enabled}">
                        <KsExecutionStatus
                            class="execution-banner__status"
                            :class="{'is-disabled': !enabled}"
                            :status="execution.state.current"
                            size="large"
                            :disabled="!enabled"
                            glow
                        >
                            <template #title>
                                <span class="status-label">
                                    {{ statusLabel }}
                                    <component :is="visible ? ChevronUp : ChevronDown" class="chevron" />
                                </span>
                            </template>
                        </KsExecutionStatus>
                    </template>
                </ChangeExecutionStatus>
            </div>

            <div class="execution-banner__top">
                <span class="execution-banner__flow">{{ execution.flowId }}</span>

                <span class="execution-banner__id">
                    <code>{{ execution.id }}</code>
                    <KsIconButton :tooltip="t('copy')" placement="top" @click="copyId">
                        <ContentCopy />
                    </KsIconButton>
                </span>

                <span v-if="replayed" class="execution-banner__replay">
                    <Replay />
                    {{ t("replayed") }}
                </span>

                <span v-if="restarted" class="execution-banner__restart">
                    <Restart />
                    {{ t("restarted") }}
                </span>
            </div>

            <div class="execution-banner__meta">
                <router-link class="meta-item" :to="createLink('namespaces', execution)">
                    <FolderOpenOutline />
                    <span>{{ execution.namespace }}</span>
                </router-link>

                <span v-if="execution.trigger?.id" class="meta-item">
                    <LightningBolt />
                    <span>{{ execution.trigger.id }}</span>
                </span>

                <KsTooltip placement="bottom-start">
                    <span class="meta-item">
                        <CalendarMonth />
                        <KsDateAgo :date="createdDate" :showTooltip="false" />
                    </span>
                    <template #content>
                        <div class="date-tooltip">
                            <div class="date-tooltip__row">
                                <span class="date-tooltip__label">{{ t("created date") }}:</span>
                                <KsDateAgo :date="createdDate" :inverted="true" :showTooltip="false" format="L LTS" />
                            </div>
                            <div v-if="scheduleDate" class="date-tooltip__row">
                                <span class="date-tooltip__label">{{ t("scheduleDate") }}:</span>
                                <KsDateAgo :date="scheduleDate" :inverted="true" :showTooltip="false" format="L LTS" />
                            </div>
                            <div class="date-tooltip__row">
                                <span class="date-tooltip__label">{{ t("latest_update") }}:</span>
                                <KsDateAgo :date="latestUpdate" :inverted="true" :showTooltip="false" format="L LTS" />
                            </div>
                        </div>
                    </template>
                </KsTooltip>

                <span v-if="username" class="meta-item">
                    <AccountOutline />
                    <span>{{ username }}</span>
                </span>

                <router-link v-if="originalLink" class="meta-item" :to="originalLink">
                    <History />
                    <span>
                        {{ t("original execution") }}:
                        <span class="meta-item__link">{{ execution.originalId }}</span>
                    </span>
                </router-link>
            </div>

            <div class="execution-banner__actions">
                <RunTimeline :histories="execution.state.histories ?? []" />

                <KsButton :icon="ContentCopy" @click="copyLogs" link>
                    {{ t("copy logs") }}
                </KsButton>

                <KsButton v-if="isFailed" class="fix-with-ai" :icon="Creation" @click="fixErrorWithAi">
                    {{ t("fix_with_ai") }}
                </KsButton>
            </div>
        </div>

        <div class="execution-banner__footer">
            <div class="execution-banner__labels">
                <LabelMultiple class="icon" />
                <div class="list">
                    <span
                        v-for="(label, idx) in execution.labels"
                        :key="idx"
                        class="label-tag"
                    >{{ label.key }}: {{ label.value }}</span>
                    <SetLabels :execution />
                </div>
            </div>

            <div class="execution-banner__stats">
                <span v-if="execution.flowRevision !== undefined" class="footer-stat">
                    <History />
                    {{ execution.flowRevision }} {{ t("revision") }}(s)
                </span>
                <span class="footer-stat">
                    <ClockTimeFourOutline />
                    <Duration :histories="execution.state.histories" />
                </span>
                <span v-if="(execution.metadata?.attemptNumber ?? 0) > 0" class="footer-stat">
                    <GraphOutline />
                    {{ execution.metadata.attemptNumber }} {{ t("attempt") }}(s)
                </span>
                <span v-if="taskCount > 0" class="footer-stat">
                    <LayersTripleOutline />
                    {{ completedTaskCount }}/{{ taskCount }} {{ t("task") }}(s)
                </span>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"

    import moment from "moment"
    import {KsExecutionStatus, State} from "@kestra-io/design-system"

    import {Execution, useExecutionsStore} from "../../../../stores/executions"
    import {useMiscStore} from "override/stores/misc"
    import * as Utils from "../../../../utils/utils"
    import {useToast} from "../../../../utils/toast"
    import {createLink} from "../utils/links"

    import ChangeExecutionStatus from "../../ChangeExecutionStatus.vue"
    import SetLabels from "../../SetLabels.vue"
    import {Duration} from "@kestra-io/topology"
    import RunTimeline from "./RunTimeline.vue"

    import AccountOutline from "vue-material-design-icons/AccountOutline.vue"
    import CalendarMonth from "vue-material-design-icons/CalendarMonth.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import ChevronUp from "vue-material-design-icons/ChevronUp.vue"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import FolderOpenOutline from "vue-material-design-icons/FolderOpenOutline.vue"
    import History from "vue-material-design-icons/History.vue"
    import LabelMultiple from "vue-material-design-icons/LabelMultiple.vue"
    import LayersTripleOutline from "vue-material-design-icons/LayersTripleOutline.vue"
    import LightningBolt from "vue-material-design-icons/LightningBolt.vue"
    import Replay from "vue-material-design-icons/Replay.vue"
    import Creation from "vue-material-design-icons/Creation.vue"
    import Restart from "vue-material-design-icons/Restart.vue"
    import ClockTimeFourOutline from "vue-material-design-icons/ClockTimeFourOutline.vue"
    import GraphOutline from "vue-material-design-icons/GraphOutline.vue"

    const props = defineProps<{execution: Execution}>()
    const emit = defineEmits<{follow: [event?: unknown]}>()

    const {t} = useI18n({useScope: "global"})
    const route = useRoute()
    const router = useRouter()
    const executionsStore = useExecutionsStore()
    const toast = useToast()

    const isFailed = computed(() => State.isFailed(props.execution.state.current))

    const statusLabel = computed(() => {
        const status = props.execution.state.current
        return status.charAt(0).toUpperCase() + status.slice(1).toLowerCase()
    })

    const matchesStatus = (type: "replay" | "replayed" | "restarted") =>
        props.execution.labels?.some(
            (label) => label.key === `system.${type}` && String(label.value) === "true",
        ) ?? false

    const replayed = computed(() => matchesStatus("replay") || matchesStatus("replayed"))
    const restarted = computed(() => matchesStatus("restarted"))

    const createdDate = computed(() =>
        moment(props.execution.state.histories?.[0]?.date).toDate(),
    )

    const scheduleDate = computed(() =>
        props.execution.scheduleDate ? moment(props.execution.scheduleDate).toDate() : undefined,
    )

    const latestUpdate = computed(() =>
        moment(
            State.isRunning(props.execution.state.current)
                ? undefined
                : props.execution.state.histories?.at(-1)?.date,
        ).toDate(),
    )

    const username = computed(() =>
        useMiscStore().configs?.edition === "OSS"
            ? undefined
            : props.execution.labels?.find((label) => label.key === "system.username")?.value,
    )

    const originalLink = computed(() => {
        const {originalId, id} = props.execution
        return originalId && originalId !== id
            ? createLink("executions", props.execution, originalId)
            : undefined
    })

    const taskCount = computed(() => props.execution.taskRunList?.length ?? 0)
    const completedTaskCount = computed(() =>
        props.execution.taskRunList?.filter(
            (tr) => !State.isRunning(tr.state?.current ?? "CREATED"),
        ).length ?? 0,
    )

    const copyId = () => {
        Utils.copy(props.execution.id)
        toast.success(t("copied"))
    }

    const copyLogs = () => {
        executionsStore
            .downloadLogs({executionId: props.execution.id})
            .then((response: unknown) => {
                Utils.copy(response as string)
                toast.success(t("copied"))
            })
    }

    const fixErrorWithAi = async () => {
        const logs = await executionsStore
            .loadLogs({
                store: false,
                executionId: props.execution.id,
                params: {minLevel: "ERROR"},
                showMessageOnError: false,
            })
            .catch(() => [])

        const errorLines = (logs ?? [])
            .map((l: {message?: string}) => l.message)
            .filter(Boolean)
            .join("\n")

        const prompt = errorLines
            ? `Fix the flow ${props.execution.flowId} as it generated the following error:\n${errorLines}`
            : `Fix the flow ${props.execution.flowId} as its execution failed.`
        window.sessionStorage.setItem("kestra-ai-prompt", prompt)

        router.push({
            name: "flows/update",
            params: {
                namespace: props.execution.namespace,
                id: props.execution.flowId,
                tab: "edit",
                tenant: route.params?.tenant,
            },
            query: {ai: "open"},
        })
    }
</script>

<style scoped lang="scss">
    .execution-banner {
        display: flex;
        flex-direction: column;
        height: 100%;
        container-type: inline-size;

        &__content {
            display: grid;
            grid-template-columns: auto minmax(0, 1fr) auto;
            align-items: center;
            align-content: center;
            column-gap: var(--ks-spacing-4);
            row-gap: var(--ks-spacing-2);
            flex: 1;
            padding: 1rem var(--ks-spacing-5);
            border-bottom: 1px solid var(--ks-border-default);
        }

        &__footer {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: var(--ks-spacing-4);
            padding: var(--ks-spacing-4) var(--ks-spacing-5);
            flex-shrink: 0;
            color: var(--ks-text-secondary);
            font-size: var(--ks-font-size-sm);
        }

        &__labels {
            display: flex;
            align-items: flex-start;
            gap: var(--ks-spacing-2);
            min-width: 0;

            .icon {
                color: var(--ks-text-muted);
                flex-shrink: 0;
                margin-top: 0.125rem;
            }

            .list {
                display: flex;
                align-items: center;
                flex-wrap: wrap;
                gap: var(--ks-spacing-2);
                min-width: 0;
            }
        }

        &__stats {
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-2);
            flex-shrink: 0;
            margin-right: calc(-1 * var(--ks-spacing-2));
        }

        &__state {
            grid-column: 1;
            grid-row: 1 / span 2;
            align-self: center;
        }

        &__top {
            grid-column: 2;
            grid-row: 1;
            display: flex;
            align-items: center;
            flex-wrap: wrap;
            gap: var(--ks-spacing-2) var(--ks-spacing-4);
        }

        &__status {
            cursor: pointer;

            &.is-disabled {
                cursor: default;
            }

            .status-label {
                display: inline-flex;
                align-items: center;
                gap: var(--ks-spacing-1);
                font-weight: 600;
            }
        }

        &__flow {
            font-weight: 700;
            font-size: var(--ks-font-size-xl);
            color: var(--ks-text-primary);
        }

        &__id {
            display: inline-flex;
            align-items: center;
            gap: var(--ks-spacing-1);
            min-width: 0;
            font-size: var(--ks-font-size-sm);
            color: var(--ks-text-secondary);

            code {
                color: var(--ks-text-primary);
                overflow-wrap: anywhere;
            }

            :deep(.kel-button) {
                color: var(--ks-text-secondary);

                &:hover {
                    color: var(--ks-text-primary);
                }
            }
        }

        &__replay,
        &__restart {
            display: inline-flex;
            align-items: center;
            gap: var(--ks-spacing-1);
            font-size: var(--ks-font-size-xs);
        }

        &__replay {
            color: var(--ks-status-info);
        }

        &__restart {
            color: var(--ks-status-warning);
        }

        &__actions {
            grid-column: 3;
            grid-row: 1 / span 2;
            align-self: center;
            display: inline-flex;
            gap: var(--ks-spacing-2);
            flex-shrink: 0;

            :deep(.kel-button) {
                color: var(--ks-text-secondary);
                font-weight: 600;
                font-size: var(--ks-font-size-sm);

                &:hover {
                    color: var(--ks-text-primary);
                }
            }

            :deep(.fix-with-ai.kel-button) {
                color: var(--ks-text-link);
                box-shadow: 0px 1px 4px 0px var(--ks-shadow-element);

                .material-design-icon {
                    color: var(--ks-text-link);
                }
            }
        }

        &__meta {
            grid-column: 2;
            grid-row: 2;
            display: flex;
            flex-wrap: wrap;
            align-items: center;
            row-gap: var(--ks-spacing-2);
            column-gap: var(--ks-spacing-4);
            margin-left: calc(-1 * var(--ks-spacing-2));
            color: var(--ks-text-secondary);
            font-size: var(--ks-font-size-sm);

            .meta-item {
                display: inline-flex;
                align-items: center;
                gap: var(--ks-spacing-1);
                padding: var(--ks-spacing-1) var(--ks-spacing-2);
                border-radius: var(--ks-radius-xs);
                color: var(--ks-text-secondary);

                .material-design-icon {
                    color: var(--ks-text-muted);
                    display: inline-flex;
                }

                &__link {
                    color: var(--ks-text-link);
                }

                &:hover {
                    background: var(--ks-bg-hover);
                    color: var(--ks-text-primary);
                }
            }

            a.meta-item:hover span {
                color: var(--ks-text-link);
            }
        }
    }

    @container (max-width: 640px) {
        .execution-banner__content {
            grid-template-columns: minmax(0, 1fr);
            align-items: start;
            row-gap: var(--ks-spacing-3);
        }

        .execution-banner__state,
        .execution-banner__top,
        .execution-banner__meta,
        .execution-banner__actions {
            grid-column: 1;
            grid-row: auto;
        }

        .execution-banner__meta {
            margin-left: 0;
            gap: var(--ks-spacing-3);
        }

        .execution-banner__actions {
            justify-self: start;
        }

        .execution-banner__footer {
            flex-direction: column;
            align-items: flex-start;
            gap: var(--ks-spacing-3);
        }
    }

    .footer-stat {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        border-radius: var(--ks-radius-xs);
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
        font-weight: 400;
        font-variant-numeric: tabular-nums;

        .material-design-icon {
            color: var(--ks-text-muted);
            display: inline-flex;
        }

        &:hover {
            background: var(--ks-bg-hover);
            color: var(--ks-text-primary);
        }
    }

    .label-tag {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        background: var(--ks-bg-tag);
        padding: 0.125rem 0.375rem;
        border-radius: 0.375rem;
        white-space: nowrap;
        color: var(--ks-text-primary);
        font-size: var(--ks-font-size-sm);
    }

    .date-tooltip {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
        font-size: var(--ks-font-size-2xs);

        &__row {
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-1);
        }

        &__label {
            color: var(--ks-text-secondary);
        }
    }
</style>
