import {defineStore} from "pinia"
import {trackFileOpen} from "../utils/tabTracking";
import {ref} from "vue";
import {useNamespacesStore} from "../override/stores/namespaces";

export interface EditorTabProps {
    name: string;
    extension?: string;
    persistent?: boolean;
    path?: string;
    flow?: boolean;
    content?: string;
    dirty?: boolean;
}

export const useEditorStore = defineStore("editor", () => {
    const onboarding = ref(false)
    const explorerVisible = ref(false)
    const explorerWidth = ref(20)
    const current = ref<EditorTabProps | undefined>(undefined)
    const tabs = ref([] as EditorTabProps[])
    const view = ref<any>()
    const treeData = ref([])
    const treeRefresh = ref(0)

    const namespaceStore = useNamespacesStore();
    function saveAllTabs({namespace}: {namespace: string}) {
        return Promise.all(
            tabs.value.map(async (tab) => {
                if(tab.flow || !tab.content) return;
                await namespaceStore.createFile({
                    namespace,
                    path: tab.path ?? tab.name,
                    content: tab.content,
                });

                setTabDirty({
                    name: tab.name,
                    path: tab.path,
                    dirty: false
                });
            })
        );
    }

    function openTab(payload: EditorTabProps) {
        const {name, extension, persistent, path, flow} = payload;

        const index = tabs.value.findIndex((tab) => {
            if (path) {
                return tab.path === path;
            }
            return tab.name === name;
        });

        let isDirty;

        if (index === -1) {
            tabs.value.push({name, extension, persistent, path, flow});
            isDirty = false;

            if (path && !flow) {
                const fileName = name || path.split("/").pop() || "";
                trackFileOpen(fileName);
            }
        } else {
            isDirty = tabs.value[index].dirty;
        }

        current.value = {
            name,
            extension,
            persistent,
            dirty: isDirty,
            path,
            flow
        }
    }

    function closeTab(payload: {name?: string, index?: number, path?: string}) {
        const {name, index, path} = payload;

        tabs.value = tabs.value.filter((tab) => {
            if (path) {
                return tab.path !== path;
            }
            return tab.name !== name;
        });

        const POSITION = index
            ? index
            : tabs.value.findIndex((tab) => {
                    if (path) {
                        return tab.path === path;
                    }
                    return tab.name === name;
                });

        if(!name) current.value = tabs.value?.[0] ?? []; // Handle tab closing by clicking the cross icon in the corner of the panel
        else if (current.value?.name === name) {
            if(POSITION - 1 >= 0){
                current.value = tabs.value[POSITION - 1];
            }else{
                current.value = tabs.value[0];
            }
        }
    }

    function updateOnboarding() {
        onboarding.value = true;
    }

    function toggleExplorerVisibility(isVisible?: boolean) {
        explorerVisible.value = isVisible ?? !explorerVisible.value;
    }

    function closeExplorer() {
        explorerVisible.value = false;
    }

    function changeExplorerWidth(width: number) {
        explorerWidth.value = width > 40 ? 40 : width < 20 ? 20 : width;
    }

    function setTabContent(payload: Partial<EditorTabProps>) {
        const tab = tabs.value.find((tab) => tab.path === payload.path);
        if(tab){
            tab.content = payload.content;
        }
    }

    function setTabDirty(payload: {name?: string, dirty: boolean, path?: string}) {
        const {name, dirty, path} =
            payload;

        const tabIdxToDirty = tabs.value.findIndex((tab) => {
            if (path) {
                return tab.path === path;
            }
            return tab.name === name;
        });

        if(tabs.value[tabIdxToDirty]) tabs.value[tabIdxToDirty].dirty = dirty;
        if(current.value) current.value.dirty = dirty;
    }

    function closeTabs() {
        if (tabs.value[0]) {
            tabs.value = [tabs.value[0]];
        }
    }

    function closeAllTabs() {
        tabs.value = [];
        current.value = undefined
    }

    function reorderTabs({from, to}: {from: number, to: number}) {
        const tab = tabs.value.splice(from, 1)[0];
        tabs.value.splice(to, 0, tab);
    }

    function refreshTree() {
        explorerVisible.value = true;
        treeRefresh.value = Date.now();
    }

    return {
        onboarding,
        explorerVisible,
        explorerWidth,
        current,
        tabs,
        view,
        treeData,
        treeRefresh,
        saveAllTabs,
        openTab,
        closeTab,
        updateOnboarding,
        toggleExplorerVisibility,
        closeExplorer,
        changeExplorerWidth,
        setTabContent,
        setTabDirty,
        closeTabs,
        closeAllTabs,
        reorderTabs,
        refreshTree
    }
})
