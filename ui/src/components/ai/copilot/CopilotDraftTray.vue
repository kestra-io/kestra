<template>
    <div v-if="actionable.length" class="copilot-tray" data-test="copilot-draft-tray">
        <div class="copilot-tray-header">
            <KsText size="small" class="copilot-tray-title">{{ $t("ai.copilot.tray.title") }}</KsText>
            <KsBadge :value="actionable.length" type="neutral" inline />
        </div>

        <KsScrollbar maxHeight="12rem">
            <ul class="copilot-tray-list">
                <li v-for="item in actionable" :key="item.draft.draftId" class="copilot-tray-item" data-test="copilot-tray-item">
                    <div class="copilot-tray-label">
                        <KsText tag="div" size="small" truncated class="copilot-tray-name">{{ item.namespace ? `${item.namespace}.${item.id}` : item.id }}</KsText>
                        <KsText tag="div" size="small" class="copilot-tray-hint" data-test="copilot-tray-hint">
                            {{ item.waitsFor ? $t("ai.copilot.tray.applyFirst", {flow: item.waitsFor}) : $t(`ai.copilot.draft.title.${item.draft.kind.toLowerCase()}`) }}
                        </KsText>
                    </div>
                    <KsButton
                        text
                        size="small"
                        class="copilot-tray-dismiss"
                        data-test="copilot-tray-dismiss"
                        @click="emit('dismiss', item.draft.draftId)"
                    >
                        {{ $t("ai.copilot.draft.dismiss") }}
                    </KsButton>
                    <KsButton
                        v-if="item.draft.kind === 'APP'"
                        size="small"
                        type="primary"
                        :disabled="Boolean(item.waitsFor)"
                        data-test="copilot-tray-open"
                        @click="openInEditor(item.draft)"
                    >
                        {{ $t("ai.copilot.draft.openInEditor") }}
                    </KsButton>
                    <KsButton
                        v-else
                        size="small"
                        type="primary"
                        :disabled="Boolean(item.waitsFor) || !item.draft.valid || applying"
                        data-test="copilot-tray-apply"
                        @click="onApply(item.draft)"
                    >
                        {{ $t("ai.copilot.draft.apply") }}
                    </KsButton>
                </li>
            </ul>
        </KsScrollbar>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useApplyDraft} from "./useApplyDraft"
    import type {PendingDraft} from "./pendingDrafts"
    import type {ArtefactDraftEvent} from "./types"

    const props = defineProps<{
        items: PendingDraft[]
    }>()

    const emit = defineEmits<{
        (e: "dismiss", draftId: string): void
        (e: "applied", draftId: string): void
    }>()

    const {applying, appSupported, dashboardSupported, testSuiteSupported, openInEditor, apply} = useApplyDraft()

    const actionable = computed(() => props.items.filter(({draft}) =>
        draft.kind === "FLOW"
        || (draft.kind === "DASHBOARD" && dashboardSupported.value)
        || (draft.kind === "APP" && appSupported)
        || (draft.kind === "TEST_SUITE" && testSuiteSupported),
    ))

    async function onApply(draft: ArtefactDraftEvent): Promise<void> {
        if (await apply(draft)) emit("applied", draft.draftId)
    }
</script>

<style scoped>
    .copilot-tray {
        padding: var(--ks-spacing-2) var(--ks-spacing-5);
        border-top: 1px solid var(--ks-border-subtle);
        background: var(--ks-bg-elevated);
    }

    .copilot-tray-header {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        margin-bottom: var(--ks-spacing-1);
    }

    .copilot-tray-title {
        font-weight: 600;
    }

    .copilot-tray-list {
        margin: 0;
        padding: 0;
        list-style: none;
    }

    .copilot-tray-item {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-1) 0;
    }

    .copilot-tray-label {
        flex: 1 1 auto;
        min-width: 0;
    }

    .copilot-tray-hint {
        --kel-text-color: var(--ks-text-secondary);
    }

    .copilot-tray-dismiss {
        --ks-button-text-color: var(--ks-text-secondary);
        color: var(--ks-text-secondary);
    }
</style>
