<template>
    <KsDialog
        :modelValue="true"
        width="min(1100px, 92vw)"
        top="4vh"
        :showClose="false"
        :closeOnPressEscape="false"
        appendToBody
        @close="emit('close')"
    >
        <template #header>
            <div class="flow-properties-modal-header">
                <span class="flow-properties-modal-title">{{ $t("no_code.sections.flow") }}</span>
                <KsIconButton
                    class="flow-properties-modal-header-action"
                    :aria-label="$t('close')"
                    :tooltip="$t('close')"
                    data-test="flow-properties-modal-close"
                    @click="emit('close')"
                >
                    <Close />
                </KsIconButton>
            </div>
        </template>

        <div class="flow-properties-modal-body">
            <FlowPropertiesEdit hideHeader hostedInModal @close="emit('close')" />
        </div>
    </KsDialog>
</template>

<script setup lang="ts">
    import {KsDialog, KsIconButton} from "@kestra-io/design-system"
    import Close from "vue-material-design-icons/Close.vue"
    import FlowPropertiesEdit from "./FlowPropertiesEdit.vue"

    const emit = defineEmits<{(e: "close"): void}>()

</script>

<style lang="scss" scoped>
    .flow-properties-modal-header {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
        width: 100%;
    }

    .flow-properties-modal-title {
        flex: 1;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    :global(.flow-properties-modal-header .flow-properties-modal-header-action) {
        flex-shrink: 0;
        margin-left: 0;
    }

    .flow-properties-modal-body {
        height: 78vh;
        min-height: 0;
        display: flex;
        flex-direction: column;
    }
</style>
