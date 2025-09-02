import {defineStore} from "pinia"
import {trackFileOpen} from "../utils/tabTracking";

export interface EditorTabProps {
    name: string;
    extension?: string;
    persistent?: boolean;
    path?: string;
    flow?: boolean;
    content?: string;
    dirty?: boolean;
}

export const useEditorStore = defineStore("editor", {
    state: () => ({
        onboarding: false,
        explorerVisible: false,
        explorerWidth: 20,
        current: undefined as EditorTabProps | undefined,
        tabs: [] as EditorTabProps[],
        view: undefined,
        treeData: [],
        treeRefresh: 0,
    }),
    actions: {
        saveAllTabs({namespace}: {namespace: string}) {
            return Promise.all(
                this.tabs.map(async (tab) => {
                    if(tab.flow) return;
                    await this.vuexStore.dispatch("namespace/createFile", {
                        namespace,
                        path: tab.path ?? tab.name,
                        content: tab.content,
                    }, {root: true});
                    this.setTabDirty({
                        name: tab.name,
                        path: tab.path,
                        dirty: false
                    });
                })
            );
        },
        openTab(payload: EditorTabProps) {
            const {name, extension, persistent, path, flow} = payload;

            const index = this.tabs.findIndex((tab) => {
                if (path) {
                    return tab.path === path;
                }
                return tab.name === name;
            });

            let isDirty;

            if (index === -1) {
                this.tabs.push({name, extension, persistent, path, flow});
                isDirty = false;
                
                if (path && !flow) {
                    const fileName = name || path.split("/").pop() || "";
                    trackFileOpen(fileName);
                }
            } else {
                isDirty = this.tabs[index].dirty;
            }

            this.current = {
                name,
                extension,
                persistent,
                dirty: isDirty,
                path,
                flow
            }
        },
        closeTab(payload: {name?: string, index?: number, path?: string}) {
            const {name, index, path} = payload;

            this.tabs = this.tabs.filter((tab) => {
                if (path) {
                    return tab.path !== path;
                }
                return tab.name !== name;
            });

            const POSITION = index
                ? index
                : this.tabs.findIndex((tab) => {
                        if (path) {
                            return tab.path === path;
                        }
                        return tab.name === name;
                    });

            if (this.current?.name === name) {
                if(POSITION - 1 >= 0){
                    this.current = this.tabs[POSITION - 1];
                }else{
                    this.current = this.tabs[0];
                }
            }
        },
        updateOnboarding() {
            this.onboarding = true;
        },
        toggleExplorerVisibility(isVisible?: boolean) {
            this.explorerVisible = isVisible ?? !this.explorerVisible;
        },
        closeExplorer() {
            this.explorerVisible = false;
        },
        changeExplorerWidth(width: number) {
            this.explorerWidth = width > 40 ? 40 : width < 20 ? 20 : width;
        },
        setTabContent(payload: Partial<EditorTabProps>) {
            const tab = this.tabs.find((tab) => tab.path === payload.path);
            if(tab){
                tab.content = payload.content;
            }
        },
        setTabDirty(payload: {name?: string, dirty: boolean, path?: string}) {
            const {name, dirty, path} =
                payload;

            const tabIdxToDirty = this.tabs.findIndex((tab) => {
                if (path) {
                    return tab.path === path;
                }
                return tab.name === name;
            });

            if(this.tabs[tabIdxToDirty]) this.tabs[tabIdxToDirty].dirty = dirty;
            if(this.current) this.current.dirty = dirty;
        },
        closeTabs() {
            if (this.tabs[0]) {
                this.tabs = [this.tabs[0]];
            }
        },
        closeAllTabs() {
            this.tabs = [];
            this.current = undefined
        },
        reorderTabs({from, to}: {from: number, to: number}) {
            const tab = this.tabs.splice(from, 1)[0];
            this.tabs.splice(to, 0, tab);
        },
        refreshTree() {
            this.explorerVisible = true;
            this.treeRefresh = Date.now();
        },
    },
})
