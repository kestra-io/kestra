import {onActivated, onDeactivated} from "vue";
import {useFlowStore} from "../../../stores/flow";

export function useKeyboardSave() {
    const flowStore = useFlowStore();
    const handleKeyboardSave = (e: KeyboardEvent) => {
        if (e.type === "keydown" && e.key === "s" && (e.ctrlKey || e.metaKey)) {
            e.preventDefault();
            flowStore.save();
        }
    };

    onActivated(() => {
        document.addEventListener("keydown", handleKeyboardSave);
    });


    onDeactivated(() => {
        document.removeEventListener("keydown", handleKeyboardSave);
    });
}