import {computed, Ref} from "vue";
import {Panel} from "../utils/multiPanelTypes";

export const usePanelDefaultSize = (panels: Ref<Panel[]>) => {
    return computed(() => panels.value.length === 0 ? 1 : (panels.value.reduce((acc, p) => acc + (p.size ?? 0), 0) / panels.value.length))
}