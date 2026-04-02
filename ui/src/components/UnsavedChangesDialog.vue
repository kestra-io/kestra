<template>
    <ks-dialog
        v-model="unsavedChangesStore.isDialogVisible"
        :title="$t('unsaved changes')"
        width="500px"
        alignCenter
        :closeOnClickModal="false"
        :closeOnPressEscape="false"
        :showClose="false"
    >
        <div class="dialog-content">
            <p>{{ $t('unsaved changes warning') }}</p>
        </div>
        <template #footer>
            <div class="dialog-footer">
                <ks-button @click="unsavedChangesStore.handleCancel">
                    {{ $t('cancel') }}
                </ks-button>
                <ks-button type="primary" @click="unsavedChangesStore.handleLeave">
                    {{ $t('leave page') }}
                </ks-button>
            </div>
        </template>
    </ks-dialog>
</template>

<script lang="ts" setup>
    import {useUnsavedChangesStore} from "../stores/unsavedChanges";

    const unsavedChangesStore = useUnsavedChangesStore();
</script>

<style scoped lang="scss">
.dialog-content {
    p {
        margin: 0;
        line-height: 1.6;
        color: var(--kbs-body-color);
        font-size: 14px;
    }
}

.dialog-footer {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
}

:deep(.kel-dialog) {
    background-color: var(--kbs-body-bg);
    border: 1px solid var(--ks-border-primary);
}

:deep(.kel-dialog__header) {
    padding: 20px 20px 10px;
    border-bottom: 1px solid var(--ks-border-primary);
}

:deep(.kel-dialog__title) {
    color: var(--kbs-body-color);
    font-size: 18px;
    font-weight: 600;
}

:deep(.kel-dialog__body) {
    padding: 20px;
    color: var(--kbs-body-color);
}

:deep(.kel-dialog__footer) {
    padding: 10px 20px 20px;
    border-top: 1px solid var(--ks-border-primary);
}

:deep(.kel-button) {
    border-radius: 4px;
}
</style>
