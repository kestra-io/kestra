<template>
    <KsDialog
        v-model="unsavedChangesStore.isDialogVisible"
        :title="$t('unsaved changes')"
        width="500px"
        alignCenter
        :closeOnClickModal="false"
        :closeOnPressEscape="false"
        :showClose="false"
    >
        <p>{{ $t('unsaved changes warning') }}</p>
        <template #footer>
            <KsButton @click="unsavedChangesStore.handleCancel">
                {{ $t('cancel') }}
            </KsButton>
            <KsButton type="primary" @click="unsavedChangesStore.handleLeave">
                {{ $t('leave page') }}
            </KsButton>
        </template>
    </KsDialog>
</template>

<script lang="ts" setup>
    import {useUnsavedChangesStore} from "../stores/unsavedChanges"

    const unsavedChangesStore = useUnsavedChangesStore()
</script>
