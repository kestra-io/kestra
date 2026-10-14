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
        </div>

        <slot />

        <KsCard class="mobile-summary-info" shadow="never">
            <div class="mobile-summary-identity">
                <router-link :to="createLink('namespaces', execution)">
                    {{ execution.namespace }}
                </router-link>
                <span>·</span>
                <KsId :value="execution.id" />
                <KsIconButton :tooltip="$t('copy')" placement="top" @click="copyId">
                    <ContentCopy />
                </KsIconButton>
            </div>

            <div class="mobile-summary-labels">
                <div ref="labelsList" class="labels-list" :class="{collapsed: !expanded}">
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
    import {computed, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import {useResizeObserver} from "@vueuse/core"
    import {dayjs, KsExecutionStatus} from "@kestra-io/design-system"
    import {Duration} from "@kestra-io/topology"

    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import ChevronUp from "vue-material-design-icons/ChevronUp.vue"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"

    import type {Execution} from "../../../../stores/executions"
    import * as Utils from "../../../../utils/utils"
    import {useToast} from "../../../../utils/toast"
    import {createLink} from "../utils/links"
    import ChangeExecutionStatus from "../../ChangeExecutionStatus.vue"
    import SetLabels from "../../SetLabels.vue"

    const props = defineProps<{execution: Execution}>()

    const {t} = useI18n({useScope: "global"})
    const toast = useToast()

    const expanded = ref(false)
    const hiddenLabels = ref(0)
    const labelsList = ref<HTMLElement>()

    const statusLabel = computed(() => {
        const status = props.execution.state.current
        return status.charAt(0).toUpperCase() + status.slice(1).toLowerCase()
    })

    const createdDate = computed(() => dayjs(props.execution.state.histories?.[0]?.date).toDate())

    useResizeObserver(labelsList, () => {
        const list = labelsList.value
        if (!list || expanded.value) return
        const visibleHeight = list.clientHeight
        hiddenLabels.value = [...list.children].filter((tag) => (tag as HTMLElement).offsetTop >= visibleHeight).length
    })

    const copyId = () => {
        Utils.copy(props.execution.id)
        toast.success(t("copied"))
    }
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
        flex-wrap: wrap;
        align-items: center;
        gap: var(--ks-spacing-1) var(--ks-spacing-2);
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
        overflow-wrap: anywhere;
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

        &.collapsed {
            max-height: calc(2 * var(--ks-spacing-6) + var(--ks-spacing-1));
            overflow: hidden;
        }
    }

    .labels-actions {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: var(--ks-spacing-2);
    }
</style>
