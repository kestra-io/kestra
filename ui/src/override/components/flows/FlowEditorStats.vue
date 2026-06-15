<template>
    <div class="flow-editor-stats">
        <FlowEditorStatCounter
            v-if="hasFlow"
            :icon="History"
            :count="revisionsCount"
            :suffix="t('flow_editor_stats.revisions.suffix')"
            :tooltip="t('flow_editor_stats.revisions.tooltip')"
            tab="revisions"
        />

        <FlowEditorStatCounter
            v-if="hasFlow"
            :icon="GraphOutline"
            :count="dependenciesCount"
            :suffix="t('flow_editor_stats.dependencies.suffix')"
            :tooltip="t('flow_editor_stats.dependencies.tooltip')"
            tab="dependencies"
        />

        <slot name="extra" />

        <ValidationError
            v-if="hasFlow"
            class="stat-validation"
            tooltipPlacement="bottom-end"
            :errors="flowStore.flowErrors"
            :warnings="flowWarnings"
            :infos="flowStore.flowInfos"
            :iconOnly="isNarrow"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, onMounted, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {useMediaQuery} from "@vueuse/core"

    import History from "vue-material-design-icons/History.vue"
    import GraphOutline from "vue-material-design-icons/GraphOutline.vue"

    import {useFlowStore} from "../../../stores/flow"
    import ValidationError from "../../../components/flows/ValidationError.vue"
    import FlowEditorStatCounter from "../../../components/flows/FlowEditorStatCounter.vue"

    const isNarrow = useMediaQuery("(max-width: 1260px)")

    const {t} = useI18n({useScope: "global"})
    const flowStore = useFlowStore()

    const hasFlow = computed(() => Boolean(flowStore.flow?.id && flowStore.flow?.namespace))
    const revisionsCount = computed(() => flowStore.revisionsCount)
    const dependenciesCount = computed(() => flowStore.dependenciesCount)

    const flowWarnings = computed(() => {
        const outdatedWarning =
            flowStore.flowValidation?.outdated && !flowStore.isCreating
                ? [`${t("outdated revision save confirmation.update.description")} ${t("outdated revision save confirmation.update.details")}`]
                : []

        const deprecationWarnings =
            flowStore.flowValidation?.deprecationPaths?.map(
                (f: string) => `${f} ${t("is deprecated")}.`,
            ) ?? []

        const otherWarnings = flowStore.flowValidation?.warnings ?? []

        const warnings = [
            ...outdatedWarning,
            ...deprecationWarnings,
            ...otherWarnings,
        ]

        return warnings.length === 0 ? undefined : warnings
    })

    function refreshStats() {
        const flow = flowStore.flow
        if (!flow?.id || !flow?.namespace) return
        if (flowStore.isCreating) return
        flowStore.loadFlowStats({namespace: flow.namespace, id: flow.id})
    }

    onMounted(refreshStats)

    watch(
        () => [flowStore.flow?.namespace, flowStore.flow?.id] as const,
        ([ns, id], [prevNs, prevId]) => {
            if (ns !== prevNs || id !== prevId) {
                flowStore.clearFlowStats()
                refreshStats()
            }
        },
    )

    watch(
        () => flowStore.flow?.revision,
        () => refreshStats(),
    )
</script>

<style scoped lang="scss">
    .flow-editor-stats {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-2) var(--ks-spacing-4);
        flex-wrap: wrap;
    }

    .stat-validation {
        display: inline-flex;
        align-items: center;
    }
</style>
