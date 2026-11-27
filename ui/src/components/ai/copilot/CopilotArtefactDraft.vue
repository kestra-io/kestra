<template>
    <div class="copilot-draft" data-test="copilot-draft">
        <div class="copilot-draft-header">
            <KsText size="small" class="copilot-draft-title">
                <KsIcon class="copilot-draft-icon"><FileDocumentOutline /></KsIcon>
                {{ $t(`ai.copilot.draft.title.${draft.kind.toLowerCase()}`) }}
            </KsText>
            <KsCodeStatus
                data-test="copilot-draft-status"
                :status="draft.valid ? 'valid' : 'error'"
                :label="draft.valid ? $t('ai.copilot.draft.valid') : $t('ai.copilot.draft.invalid')"
            />
        </div>

        <KsAlert
            v-if="!draft.valid && draft.constraints"
            type="warning"
            :closable="false"
            class="copilot-draft-constraints"
            data-test="copilot-draft-constraints"
        >
            {{ draft.constraints }}
        </KsAlert>

        <div v-if="draft.kind === 'FLOW' && draft.valid" class="copilot-draft-view">
            <KsTabs v-model="view" type="segmented" data-test="copilot-draft-view">
                <KsTabPane name="yaml" :label="$t('flow_change_review.yaml')" />
                <KsTabPane name="graph" :label="$t('flow_change_review.graph')" />
            </KsTabs>
        </div>
        <div
            v-if="view === 'graph'"
            v-ks-loading="base === undefined && !baseError"
            class="copilot-draft-graph"
            data-test="copilot-draft-graph"
        >
            <KsAlert
                v-if="baseError"
                type="warning"
                :closable="false"
                :title="$t('flow_change_review.graph_error')"
                data-test="copilot-draft-graph-error"
            />
            <FlowChangeReview
                v-else-if="base !== undefined"
                :before="base || undefined"
                :after="draft.yaml"
            />
        </div>
        <KsMarkdown
            v-else
            class="copilot-draft-yaml"
            data-test="copilot-draft-yaml"
            :content="yamlBlock"
        />

        <div v-if="dismissed" class="copilot-draft-footer" data-test="copilot-draft-dismissed">
            <KsText size="small" class="copilot-draft-status-label">{{ $t("ai.copilot.draft.dismissed") }}</KsText>
        </div>
        <div v-else-if="applied" class="copilot-draft-footer" data-test="copilot-draft-applied">
            <KsText size="small" class="copilot-draft-status-label">{{ $t("ai.copilot.draft.applied") }}</KsText>
        </div>
        <div v-else class="copilot-draft-footer">
            <KsButton
                text
                size="small"
                class="copilot-draft-dismiss"
                data-test="copilot-draft-dismiss"
                @click="emit('dismiss', draft.draftId)"
            >
                {{ $t("ai.copilot.draft.dismiss") }}
            </KsButton>
            <template v-if="showActions">
                <KsButton size="small" data-test="copilot-draft-open" @click="openInEditor(draft)">
                    {{ $t("ai.copilot.draft.openInEditor") }}
                </KsButton>
                <KsButton
                    v-if="draft.kind !== 'APP'"
                    size="small"
                    type="primary"
                    :disabled="!draft.valid || applying"
                    data-test="copilot-draft-apply"
                    @click="onApply"
                >
                    {{ $t("ai.copilot.draft.apply") }}
                </KsButton>
            </template>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, defineAsyncComponent, ref, watch} from "vue"
    import FileDocumentOutline from "vue-material-design-icons/FileDocumentOutline.vue"
    import {useApplyDraft} from "./useApplyDraft"
    import type {ArtefactDraftEvent} from "./types"

    const props = defineProps<{
        draft: ArtefactDraftEvent
        dismissed?: boolean
        applied?: boolean
    }>()

    const emit = defineEmits<{
        (e: "dismiss", draftId: string): void
        (e: "applied", draftId: string): void
    }>()

    const FlowChangeReview = defineAsyncComponent(
        () => import("../../flows/changeReview/FlowChangeReview.vue"),
    )

    const {
        applying,
        appSupported,
        dashboardSupported,
        testSuiteSupported,
        openInEditor,
        apply,
        draftBaseSource,
    } = useApplyDraft()

    const view = ref<"yaml" | "graph">("yaml")
    const base = ref<string>()
    const baseError = ref(false)

    const showActions = computed(
        () => props.draft.kind === "FLOW"
            || (props.draft.kind === "DASHBOARD" && dashboardSupported.value)
            || (props.draft.kind === "APP" && appSupported)
            || (props.draft.kind === "TEST_SUITE" && testSuiteSupported),
    )

    watch(view, async (value) => {
        if (value !== "graph" || base.value !== undefined) {
            return
        }
        baseError.value = false
        try {
            base.value = await draftBaseSource(props.draft)
        } catch {
            baseError.value = true
        }
    })

    async function onApply(): Promise<void> {
        if (await apply(props.draft)) emit("applied", props.draft.draftId)
    }

    const yamlBlock = computed(() => "```yaml\n" + props.draft.yaml + "\n```")
</script>

<style scoped>
    .copilot-draft {
        width: 100%;
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-lg);
        overflow: hidden;
        background: var(--ks-bg-base);
    }

    .copilot-draft-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        background: var(--ks-bg-elevated);
    }

    .copilot-draft-title {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        font-size: var(--ks-font-size-sm);
    }

    .copilot-draft-constraints {
        margin: var(--ks-spacing-2) var(--ks-spacing-3) 0;
    }

    .copilot-draft-view {
        padding: var(--ks-spacing-2) var(--ks-spacing-3) 0;
    }

    .copilot-draft-graph {
        min-height: 10rem;
        padding: var(--ks-spacing-3);
    }

    .copilot-draft-yaml {
        margin: 0;
        max-height: 18rem;
        overflow: auto;
        padding: var(--ks-spacing-3);
        font-size: var(--ks-font-size-sm);
    }

    .copilot-draft-footer {
        display: flex;
        justify-content: flex-end;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        background: var(--ks-bg-elevated);
    }

    .copilot-draft-dismiss {
        --ks-button-text-color: var(--ks-text-secondary);
        color: var(--ks-text-secondary);
    }

    .copilot-draft-status-label {
        --kel-text-color: var(--ks-text-muted);
        font-style: italic;
    }
</style>
