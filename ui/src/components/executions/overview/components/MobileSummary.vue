<template>
    <div class="mobile-summary" data-test="execution-mobile-summary">
        <div class="mobile-summary-head">
            <ChangeExecutionStatus :execution>
                <template #trigger="{visible, enabled}">
                    <KsExecutionStatus
                        :status="execution.state.current"
                        :disabled="!enabled"
                        :clickable="enabled"
                    >
                        <template #title>
                            <span class="status-label">
                                {{ statusLabel }}
                                <component v-if="enabled" :is="visible ? ChevronUp : ChevronDown" />
                            </span>
                        </template>
                    </KsExecutionStatus>
                </template>
            </ChangeExecutionStatus>
            <span class="mobile-summary-meta">
                <KsDateAgo :date="createdDate" :showTooltip="false" />
                <span>·</span>
                <Duration :histories="execution.state.histories" />
                <template v-if="execution.flowRevision !== undefined">
                    <span>·</span>
                    <span>{{ $t("execution_mobile.revision_short", {revision: execution.flowRevision}) }}</span>
                </template>
            </span>
            <router-link v-if="replayed" class="mobile-summary-marker is-replay" :to="replaysLink">
                <Replay />
                {{ $t("replayed") }}
            </router-link>
            <span v-if="restarted" class="mobile-summary-marker is-restart">
                <Restart />
                {{ $t("restarted") }}
            </span>
        </div>

        <slot />

        <KsCard class="mobile-summary-info" shadow="never">
            <div class="mobile-summary-identity">
                <router-link class="identity-text" :to="createLink('flows', execution)">
                    {{ execution.flowId }}
                </router-link>
                <span>·</span>
                <router-link class="identity-text" :to="createLink('namespaces', execution)">
                    {{ execution.namespace }}
                </router-link>
                <span>·</span>
                <KsId :value="execution.id" />
                <KsIconButton :tooltip="$t('copy')" placement="top" @click="copyId">
                    <ContentCopy />
                </KsIconButton>
            </div>

            <div class="mobile-summary-labels">
                <div ref="labelsList" class="labels-list" :style="collapsedStyle">
                    <KsTag v-for="label in execution.labels" :key="`${label.key}-${label.value}`" size="small" truncate>
                        {{ label.key }}: {{ label.value }}
                    </KsTag>
                </div>
                <div class="labels-actions">
                    <KsButton v-if="hiddenLabels > 0" link size="small" data-test="toggle-labels" @click="expanded = !expanded">
                        {{ expanded ? $t("execution_mobile.fewer_labels") : $t("execution_mobile.more_labels", hiddenLabels) }}
                    </KsButton>
                    <SetLabels v-if="expanded || !execution.labels?.length" :execution />
                </div>
            </div>
        </KsCard>
    </div>
</template>

<script setup lang="ts">
    import {computed, nextTick, ref, watch} from "vue"
    import {useResizeObserver} from "@vueuse/core"
    import {KsExecutionStatus} from "@kestra-io/design-system"
    import {Duration} from "@kestra-io/topology"

    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import ChevronUp from "vue-material-design-icons/ChevronUp.vue"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import Replay from "vue-material-design-icons/Replay.vue"
    import Restart from "vue-material-design-icons/Restart.vue"

    import type {Execution} from "../../../../stores/executions"
    import {useExecutionSummary} from "../../../../composables/useExecutionSummary"
    import {createLink} from "../utils/links"
    import ChangeExecutionStatus from "../../ChangeExecutionStatus.vue"
    import SetLabels from "../../SetLabels.vue"

    const VISIBLE_LABEL_ROWS = 2

    const props = defineProps<{execution: Execution}>()

    const {statusLabel, replayed, restarted, createdDate, replaysLink, copyId} = useExecutionSummary(() => props.execution)

    const expanded = ref(false)
    const hiddenLabels = ref(0)
    const collapsedHeight = ref<number>()
    const labelsList = ref<HTMLElement>()

    const collapsedStyle = computed(() =>
        !expanded.value && collapsedHeight.value !== undefined
            ? {maxHeight: `${collapsedHeight.value}px`, overflow: "hidden"}
            : undefined,
    )

    function measureLabels() {
        const list = labelsList.value
        if (!list) return
        const tags = [...list.children] as HTMLElement[]
        const rowTops = [...new Set(tags.map((tag) => tag.offsetTop))].sort((a, b) => a - b)
        const firstHiddenTop = rowTops[VISIBLE_LABEL_ROWS]
        if (firstHiddenTop === undefined) {
            hiddenLabels.value = 0
            collapsedHeight.value = undefined
            return
        }
        const rowGap = Number.parseFloat(getComputedStyle(list).rowGap) || 0
        hiddenLabels.value = tags.filter((tag) => tag.offsetTop >= firstHiddenTop).length
        collapsedHeight.value = firstHiddenTop - rowTops[0] - rowGap
    }

    useResizeObserver(labelsList, measureLabels)

    watch(() => props.execution.labels, async () => {
        await nextTick()
        measureLabels()
    }, {deep: true})
</script>

<style scoped lang="scss">
    .mobile-summary {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-3);
        min-width: 0;
    }

    .mobile-summary-head {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: var(--ks-spacing-2) var(--ks-spacing-3);
    }

    .status-label {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
    }

    .mobile-summary-meta {
        display: inline-flex;
        flex-wrap: wrap;
        align-items: center;
        gap: var(--ks-spacing-1) var(--ks-spacing-2);
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
    }

    .mobile-summary-info {
        --kel-card-padding: var(--ks-spacing-3);
    }

    .mobile-summary-identity {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);

        > * {
            flex: none;
        }

        .identity-text {
            flex: 0 1 auto;
            min-width: 0;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
    }

    .mobile-summary-labels {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: var(--ks-spacing-1);
        margin-top: var(--ks-spacing-2);
    }

    .labels-list {
        position: relative;
        display: flex;
        flex-wrap: wrap;
        gap: var(--ks-spacing-1) var(--ks-spacing-2);
        max-width: 100%;
    }

    .mobile-summary-marker {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        font-size: var(--ks-font-size-xs);
        text-decoration: none;

        &.is-replay {
            color: var(--ks-status-info);
        }

        &.is-restart {
            color: var(--ks-status-warning);
        }
    }

    .labels-actions {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: var(--ks-spacing-2);
    }
</style>
