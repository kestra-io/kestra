<template>
    <div class="execution-banner">
        <div class="execution-banner__content">
            <div class="execution-banner__state">
                <ChangeExecutionStatus :execution>
                    <template #trigger="{visible, enabled}">
                        <KsExecutionStatus
                            class="execution-banner__status"
                            :status="execution.state.current"
                            size="large"
                            :disabled="!enabled"
                            :clickable="enabled"
                            glow
                        >
                            <template #title>
                                <span class="status-label">
                                    {{ statusLabel }}
                                    <component v-if="enabled" :is="visible ? ChevronUp : ChevronDown" class="chevron" />
                                </span>
                            </template>
                        </KsExecutionStatus>
                    </template>
                </ChangeExecutionStatus>
            </div>

            <div class="execution-banner__top">
                <router-link class="execution-banner__flow" :to="createLink('flows', execution)">
                    {{ execution.flowId }}
                </router-link>

                <span class="execution-banner__id">
                    <code>{{ execution.id }}</code>
                    <KsIconButton :tooltip="$t('copy')" placement="top" @click="copyId">
                        <ContentCopy />
                    </KsIconButton>
                </span>

                <router-link v-if="replayed" class="execution-banner__replay" :to="replaysLink">
                    <Replay />
                    {{ $t("replayed") }}
                </router-link>

                <span v-if="restarted" class="execution-banner__restart">
                    <Restart />
                    {{ $t("restarted") }}
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
                                <span class="date-tooltip__label">{{ $t("created date") }}:</span>
                                <KsDateAgo :date="createdDate" :inverted="true" :showTooltip="false" format="L LTS" />
                            </div>
                            <div v-if="scheduleDate" class="date-tooltip__row">
                                <span class="date-tooltip__label">{{ $t("scheduleDate") }}:</span>
                                <KsDateAgo :date="scheduleDate" :inverted="true" :showTooltip="false" format="L LTS" />
                            </div>
                            <div class="date-tooltip__row">
                                <span class="date-tooltip__label">{{ $t("latest_update") }}:</span>
                                <KsDateAgo :date="latestUpdate" :inverted="true" :showTooltip="false" format="L LTS" />
                            </div>
                        </div>
                    </template>
                </KsTooltip>

                <span v-if="username" class="meta-item">
                    <AccountOutline />
                    <span>{{ username }}</span>
                </span>

                <ParentExecutionLink :execution />

                <component
                    :is="relation"
                    v-for="(relation, index) in executionBannerRelations"
                    :key="index"
                    :execution
                />
            </div>

            <div class="execution-banner__actions">
                <RunTimeline :histories="execution.state.histories ?? []" />

                <KsButton :icon="ContentCopy" @click="copyLogs" link>
                    {{ $t("copy logs") }}
                </KsButton>

                <KsButton v-if="aiEnabled && isFailed" class="fix-with-ai" :icon="Creation" @click="fixErrorWithAi">
                    {{ $t("fix_with_ai") }}
                </KsButton>
            </div>
        </div>

        <div class="execution-banner__footer">
            <div class="execution-banner__tags">
                <div class="execution-banner__labels">
                    <LabelMultiple class="icon" />
                    <div class="list">
                        <SetLabels :execution />
                        <span
                            v-for="(label, idx) in execution.labels"
                            :key="idx"
                            class="label-tag"
                        >{{ label.key }}: {{ label.value }}</span>
                    </div>
                </div>

                <div v-if="searchableOutputs.length" class="execution-banner__searchable" data-test="searchable-outputs">
                    <Magnify class="icon" :title="$t('execution_banner.searchable_values')" />
                    <div class="list">
                        <span
                            v-for="(output, index) in searchableOutputs"
                            :key="`${output.outputId}-${index}`"
                            class="label-tag searchable-tag"
                            :title="`${output.outputId}: ${output.value}`"
                        >{{ output.outputId }}: {{ output.value }}</span>
                        <span v-if="remainingSearchableOutputs > 0" class="more-searchable" data-test="more-searchable-outputs">
                            {{ $t("execution_banner.more_searchable_values", {count: $n(remainingSearchableOutputs)}) }}
                        </span>
                    </div>
                </div>
            </div>

            <div class="execution-banner__stats">
                <span v-if="execution.flowRevision !== undefined" class="footer-stat">
                    <History />
                    {{ $t("execution_banner.revision", {revision: execution.flowRevision}) }}
                </span>
                <span class="footer-stat">
                    <ClockTimeFourOutline />
                    <Duration :histories="execution.state.histories" />
                </span>
                <span v-if="(execution.metadata?.attemptNumber ?? 0) > 0" class="footer-stat">
                    <GraphOutline />
                    {{ $t("execution_banner.attempts", execution.metadata?.attemptNumber ?? 0) }}
                </span>
                <span v-if="taskCount > 0" class="footer-stat">
                    <LayersTripleOutline />
                    {{ $t("execution_banner.tasks", {completed: completedTaskCount}, taskCount) }}
                </span>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"

    import {dayjs, KsExecutionStatus, State} from "@kestra-io/design-system"
    import * as OutputsAPI from "@kestra-io/kestra-sdk/outputs"
    import type {OutputControllerSearchableOutputInformation} from "@kestra-io/kestra-sdk"

    import {Execution, useExecutionsStore} from "../../../stores/executions"
    import {useExecutionSummary} from "../../../composables/useExecutionSummary"
    import {useMiscStore} from "override/stores/misc"
    import {useAiEnabled} from "../../../composables/useAiEnabled"
    import * as Utils from "../../../utils/utils"
    import {useToast} from "../../../utils/toast"
    import {handledIf} from "../../../utils/kestraHttp"
    import {createLink} from "../overview/utils/links"
    import {executionBannerRelations} from "override/components/executions/overview/OverviewExtensions"

    import ChangeExecutionStatus from "../ChangeExecutionStatus.vue"
    import SetLabels from "../SetLabels.vue"
    import {Duration} from "@kestra-io/topology"
    import RunTimeline from "./RunTimeline.vue"
    import ParentExecutionLink from "../overview/components/ParentExecutionLink.vue"

    import AccountOutline from "vue-material-design-icons/AccountOutline.vue"
    import CalendarMonth from "vue-material-design-icons/CalendarMonth.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import ChevronUp from "vue-material-design-icons/ChevronUp.vue"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import FolderOpenOutline from "vue-material-design-icons/FolderOpenOutline.vue"
    import History from "vue-material-design-icons/History.vue"
    import LabelMultiple from "vue-material-design-icons/LabelMultiple.vue"
    import LayersTripleOutline from "vue-material-design-icons/LayersTripleOutline.vue"
    import Magnify from "vue-material-design-icons/Magnify.vue"
    import LightningBolt from "vue-material-design-icons/LightningBolt.vue"
    import Replay from "vue-material-design-icons/Replay.vue"
    import Creation from "vue-material-design-icons/Creation.vue"
    import Restart from "vue-material-design-icons/Restart.vue"
    import ClockTimeFourOutline from "vue-material-design-icons/ClockTimeFourOutline.vue"
    import GraphOutline from "vue-material-design-icons/GraphOutline.vue"

    const props = defineProps<{execution: Execution}>()

    const {t} = useI18n({useScope: "global"})
    const executionsStore = useExecutionsStore()
    const toast = useToast()

    const aiEnabled = useAiEnabled()
    const isFailed = computed(() => State.isFailed(props.execution.state.current))

    const {statusLabel, replayed, restarted, createdDate, replaysLink, copyId} = useExecutionSummary(() => props.execution)

    const scheduleDate = computed(() =>
        props.execution.scheduleDate ? dayjs(props.execution.scheduleDate).toDate() : undefined,
    )

    const latestUpdate = computed(() =>
        dayjs(
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

    const taskCount = computed(() => props.execution.taskRunList?.length ?? 0)
    const completedTaskCount = computed(() =>
        props.execution.taskRunList?.filter(
            (tr) => !State.isRunning(tr.state?.current ?? "CREATED"),
        ).length ?? 0,
    )

    const SEARCHABLE_OUTPUTS_LIMIT = 10

    const searchableOutputs = ref<OutputControllerSearchableOutputInformation[]>([])
    const searchableOutputsTotal = ref(0)
    const remainingSearchableOutputs = computed(() => searchableOutputsTotal.value - searchableOutputs.value.length)

    // Outputs are written as the execution progresses, so reload when its state changes too.
    watch(
        () => [props.execution.id, props.execution.state.current] as const,
        async ([executionId], previous, onCleanup) => {
            if (executionId !== previous?.[0]) {
                searchableOutputs.value = []
                searchableOutputsTotal.value = 0
            }

            let superseded = false
            onCleanup(() => {
                superseded = true
            })

            const page = await OutputsAPI.listSearchableOutputs({executionId, page: 1, size: SEARCHABLE_OUTPUTS_LIMIT})
                .catch((e: unknown) => {
                    handledIf(e, [403, 404])
                    return undefined
                })
            if (superseded) return

            searchableOutputs.value = page?.results ?? []
            searchableOutputsTotal.value = page?.total ?? 0
        },
        {immediate: true},
    )

    const copyLogs = () => {
        executionsStore
            .downloadLogs({executionId: props.execution.id})
            .then((response: unknown) => {
                Utils.copy(response as string)
                toast.success(t("copied"))
            })
    }

    // The prompt only needs the first errors, so an execution with thousands of them is not downloaded whole.
    const FIX_WITH_AI_LOG_LIMIT = 100

    const fixErrorWithAi = async () => {
        const logs = await executionsStore
            .loadLogs({
                store: false,
                executionId: props.execution.id,
                filters: [{field: "level", operation: "GREATER_THAN_OR_EQUAL_TO", value: "ERROR"}],
                page: 1,
                size: FIX_WITH_AI_LOG_LIMIT,
            })
            .catch((e: unknown) => {
                handledIf(e, [403, 404])
                return []
            })

        const errorLines = (logs ?? [])
            .map((l: {message?: string}) => l.message)
            .filter(Boolean)
            .join("\n")

        const prompt = errorLines
            ? `Fix the flow ${props.execution.flowId} as it generated the following error:\n${errorLines}`
            : `Fix the flow ${props.execution.flowId} as its execution failed.`
        useMiscStore().promptCopilot(prompt, {title: t("ai.copilot.fixThread.execution", {id: props.execution.flowId}), newThread: true})
    }
</script>

<style scoped>
    .execution-banner {
        display: flex;
        flex-direction: column;
        height: 100%;
        container-type: inline-size;
    }

    .execution-banner__content {
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

    .execution-banner__footer {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--ks-spacing-4);
        padding: var(--ks-spacing-4) var(--ks-spacing-5);
        flex-shrink: 0;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
    }

    .execution-banner__tags {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        min-width: 0;
    }

    .execution-banner__labels,
    .execution-banner__searchable {
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

    .execution-banner__stats {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        flex-shrink: 0;
        align-self: flex-start;
        margin-right: calc(-1 * var(--ks-spacing-2));
    }

    .execution-banner__state {
        grid-column: 1;
        grid-row: 1 / span 2;
        align-self: center;
    }

    .execution-banner__top {
        grid-column: 2;
        grid-row: 1;
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: var(--ks-spacing-2) var(--ks-spacing-4);
    }

    .execution-banner__status {
        .status-label {
            display: inline-flex;
            align-items: center;
            gap: var(--ks-spacing-1);
            font-weight: 600;
        }
    }

    .execution-banner__flow {
        font-weight: 700;
        font-size: var(--ks-font-size-xl);
        color: var(--ks-text-primary);
        text-decoration: none;

        &:hover {
            color: var(--ks-text-link);
            text-decoration: underline;
        }
    }

    .execution-banner__id {
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

        & :deep(.kel-button) {
            color: var(--ks-text-secondary);

            &:hover {
                color: var(--ks-text-primary);
            }
        }
    }

    .execution-banner__replay, .execution-banner__restart {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        font-size: var(--ks-font-size-xs);
    }

    .execution-banner__replay {
        color: var(--ks-status-info);
        text-decoration: none;

        &:hover {
            text-decoration: underline;
        }
    }

    .execution-banner__restart {
        color: var(--ks-status-warning);
    }

    .execution-banner__actions {
        grid-column: 3;
        grid-row: 1 / span 2;
        align-self: center;
        display: inline-flex;
        gap: var(--ks-spacing-2);
        flex-shrink: 0;

        & :deep(.kel-button) {
            color: var(--ks-text-secondary);
            font-weight: 600;
            font-size: var(--ks-font-size-sm);

            &:hover {
                color: var(--ks-text-primary);
            }
        }

        & :deep(.fix-with-ai.kel-button) {
            color: var(--ks-text-link);
            box-shadow: 0px 1px 4px 0px var(--ks-shadow-element);

            .material-design-icon {
                color: var(--ks-text-link);
            }
        }
    }

    .execution-banner__meta {
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
        }

        .meta-item {
            &:hover {
                background: var(--ks-bg-hover);
                color: var(--ks-text-primary);
            }
        }

        a.meta-item:hover span {
            color: var(--ks-text-link);
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

    .searchable-tag {
        display: inline-block;
        max-width: 20rem;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    .more-searchable {
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
        font-weight: 600;
    }

    .date-tooltip {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
        font-size: var(--ks-font-size-2xs);
    }

    .date-tooltip__row {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-1);
    }

    .date-tooltip__label {
        color: var(--ks-text-secondary);
    }
</style>
