<template>
    <div class="flow-run-actions">
        <KsButton
            v-if="flowRun?.canPrefill"
            class="prefill-button"
            :icon="ContentCopy"
            @click="flowRun?.prefill()"
        >
            {{ $t("prefill inputs") }}
        </KsButton>
        <span v-if="flowRun?.showExecuteButton" data-onboarding-target="flow-execute-confirm-button">
            <KsButton
                class="flow-run-trigger-button"
                type="primary"
                :icon="flowRun?.buttonIcon"
                :disabled="!flowRun?.flowCanBeExecuted || flowRun?.hasBlockingChecks"
                :data-test="flowRun?.buttonTestId"
                @click="flowRun?.submit()"
            >
                {{ $t(flowRun?.buttonText ?? "launch execution") }}
            </KsButton>
        </span>
    </div>
</template>

<script setup lang="ts">
    import type {Component} from "vue"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"

    interface FlowRunInstance {
        submit: () => void
        prefill: () => void
        canPrefill: boolean
        flowCanBeExecuted: boolean
        hasBlockingChecks: boolean
        buttonText: string
        buttonIcon: Component
        buttonTestId: string
        showExecuteButton: boolean
    }

    defineProps<{flowRun: FlowRunInstance | null}>()
</script>

<style scoped lang="scss">
.flow-run-actions {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: var(--ks-spacing-2);
}

.prefill-button {
    margin-right: auto;
}
</style>
