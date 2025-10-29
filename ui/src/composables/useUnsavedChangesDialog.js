import {ref} from "vue";

const isDialogVisible = ref(false);
let resolveCallback = null;

export function useUnsavedChangesDialog() {
    const showDialog = () => {
        return new Promise((resolve) => {
            isDialogVisible.value = true;
            resolveCallback = resolve;
        });
    };

    const handleLeave = () => {
        isDialogVisible.value = false;
        if (resolveCallback) {
            resolveCallback(true);
            resolveCallback = null;
        }
    };

    const handleCancel = () => {
        isDialogVisible.value = false;
        if (resolveCallback) {
            resolveCallback(false);
            resolveCallback = null;
        }
    };

    return {
        isDialogVisible,
        showDialog,
        handleLeave,
        handleCancel,
    };
}
