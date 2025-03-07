import {computed, h} from "vue";
import {useStore} from "vuex";
import {EDITOR_ELEMENTS} from "./panelDefinition";

export function useCodeTabs(){
    const store = useStore();

    const codeTabsFromStore = computed<
        Array<{
            name:string,
            extension: string,
            path: string,
            persistent: boolean,
            flow: boolean
        }>
    >(() => store.state.editor.tabs);

    const codeTabs = computed(() => {
        const templateElement = EDITOR_ELEMENTS.find(e => e.value === "code")
        if(!templateElement){
            throw new Error("Code element not found")
        }
        return codeTabsFromStore.value.map(t => {
            const element = {
                ...templateElement,
                value: `code-${t.name}`,
                button: {
                    ...templateElement.button,
                    label: t.name
                },
                component: () => h(templateElement.component, t),
            }
            return element
        })
    })

    return {codeTabs}
}