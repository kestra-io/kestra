import {defineStore} from "pinia";
import {ref} from "vue";

export const useUnsavedChangesStore = defineStore("unsavedChanges", () => {
    const isDialogVisible = ref(false);
    let resolveCallback: ((value: boolean) => void) | null = null;
    const unsavedChange = ref(false)
    
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
        unsavedChange,
    };
});
