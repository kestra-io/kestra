import {ComputedRef, onActivated, onDeactivated} from "vue";
import {useFlowStore} from "../../../stores/flow";

export function useKeyboardSave(flowSource: ComputedRef<string>) {
    const flowStore = useFlowStore();
    const handleKeyboardSave = (e: KeyboardEvent) => {
        if (e.type === "keydown" && e.key === "s" && e.ctrlKey) {
            e.preventDefault();
            flowStore.save({
                content: flowSource.value
            });
        }
    };

    onActivated(() => {
        document.addEventListener("keydown", handleKeyboardSave);
    });


    onDeactivated(() => {
        document.removeEventListener("keydown", handleKeyboardSave);
    });
}