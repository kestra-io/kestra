<template>
    <el-dialog
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
                <el-button @click="unsavedChangesStore.handleCancel">
                    {{ $t('cancel') }}
                </el-button>
                <el-button type="primary" @click="unsavedChangesStore.handleLeave">
                    {{ $t('leave page') }}
                </el-button>
            </div>
        </template>
    </el-dialog>
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
        color: var(--bs-body-color);
        font-size: 14px;
    }
}

.dialog-footer {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
}

:deep(.el-dialog) {
    background-color: var(--bs-body-bg);
    border: 1px solid var(--bs-border-color);
}

:deep(.el-dialog__header) {
    padding: 20px 20px 10px;
    border-bottom: 1px solid var(--bs-border-color);
}

:deep(.el-dialog__title) {
    color: var(--bs-body-color);
    font-size: 18px;
    font-weight: 600;
}

:deep(.el-dialog__body) {
    padding: 20px;
    color: var(--bs-body-color);
}

:deep(.el-dialog__footer) {
    padding: 10px 20px 20px;
    border-top: 1px solid var(--bs-border-color);
}

:deep(.el-button) {
    border-radius: 4px;
}
</style>
