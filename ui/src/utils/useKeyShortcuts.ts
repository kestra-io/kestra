import {ref} from "vue";

const isKeyShortcutsDialogShown = ref(false);

export function useKeyShortcuts() {
    function showKeyShortcuts() {
        isKeyShortcutsDialogShown.value = true;
    }

    function hideKeyShortcuts() {
        isKeyShortcutsDialogShown.value = false;
    }

    return {
        isKeyShortcutsDialogShown,
        showKeyShortcuts,
        hideKeyShortcuts
    };
}
