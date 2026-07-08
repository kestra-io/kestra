<template>
    <KsCard shadow="never" class="proposed-action" data-test="copilot-proposed-action">
        <div class="proposed-action-header">
            <KsIcon><LightbulbOnOutline /></KsIcon>
            <KsText size="small">
                {{ isPlan ? t("ai.copilot.confirm.planTitle") : t("ai.copilot.confirm.actionTitle") }}
            </KsText>
            <KsTag v-if="action.family" size="small">{{ action.family }}</KsTag>
        </div>

        <KsText size="small" class="proposed-action-summary">{{ action.summary }}</KsText>

        <KsInput
            v-model="reason"
            type="textarea"
            :autosize="{minRows: 1, maxRows: 3}"
            :placeholder="t('ai.copilot.confirm.reasonPlaceholder')"
            :disabled="disabled"
            data-test="copilot-confirm-reason"
        />

        <div class="proposed-action-buttons">
            <KsButton
                :disabled="disabled"
                data-test="copilot-reject"
                @click="emit('reject', reason.trim() || undefined)"
            >
                {{ t("ai.copilot.confirm.reject") }}
            </KsButton>
            <KsButton
                type="primary"
                :disabled="disabled"
                data-test="copilot-approve"
                @click="emit('approve', reason.trim() || undefined)"
            >
                {{ isPlan ? t("ai.copilot.confirm.approveExecute") : t("ai.copilot.confirm.approve") }}
            </KsButton>
        </div>
    </KsCard>
</template>

<script setup lang="ts">
    import {ref, computed} from "vue"
    import {useI18n} from "vue-i18n"
    import LightbulbOnOutline from "vue-material-design-icons/LightbulbOnOutline.vue"
    import type {ProposedActionEvent} from "./types"

    const props = defineProps<{
        action: ProposedActionEvent
        /** Disabled once a decision has been sent (stream in flight). */
        disabled?: boolean
    }>()

    const emit = defineEmits<{
        (e: "approve", reason?: string): void
        (e: "reject", reason?: string): void
    }>()

    const {t} = useI18n()

    const reason = ref("")

    // A Plan-mode plan card carries no concrete tool call.
    const isPlan = computed(() => !props.action.tool)
</script>

<style scoped>
    .proposed-action {
        border: 1px solid var(--ks-border-default);
    }

    .proposed-action-header {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        margin-bottom: var(--ks-spacing-2);
    }

    .proposed-action-summary {
        display: block;
        margin-bottom: var(--ks-spacing-3);
        color: var(--ks-text-secondary);
    }

    .proposed-action-buttons {
        display: flex;
        justify-content: flex-end;
        gap: var(--ks-spacing-2);
        margin-top: var(--ks-spacing-3);
    }
</style>
