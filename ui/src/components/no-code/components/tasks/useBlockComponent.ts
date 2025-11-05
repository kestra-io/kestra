import {h, inject, onMounted, ref} from "vue"
import {SCHEMA_DEFINITIONS_INJECTION_KEY} from "../../injectionKeys";

export function useBlockComponent() {
    const definitionsRef = inject(SCHEMA_DEFINITIONS_INJECTION_KEY);
    const definitions = definitionsRef?.value ?? {};

    const getBlockComponent = ref<(property: any, key?: string) => any>(() => {
        return h("div", {class: "no-code-skeleton"}, "Loading...");
    })
    
    onMounted(async () => {
        const module = await import("./getTaskComponent");
        getBlockComponent.value = (property: any, key?: string) => module.getTaskComponent(property, definitions, key);
    })

    return {
        getBlockComponent
    }
}