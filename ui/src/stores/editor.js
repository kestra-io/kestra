export default {
    namespaced: true,
    state: {
        onboarding: false,
        explorerVisible: false,
        explorerWidth: 20,
        current: undefined,
        tabs: [],
        view: undefined,
        treeData: [],
    },
    actions: {
        saveAllTabs({dispatch, state}, {namespace}) {
            return Promise.all(
                state.tabs.map(async (tab) => {
                    if(tab.flow) return;
                    await dispatch("namespace/createFile", {
                        namespace,
                        path: tab.path ?? tab.name,
                        content: tab.content,
                    }, {root: true});
                    tab.dirty = false;
                })
            );
        },
        openTab({commit, state}, payload) {
            const {name, extension, persistent, path, flow} = payload;

            const index = state.tabs.findIndex((tab) => {
                if (path) {
                    return tab.path === path;
                }
                return tab.name === name;
            });

            let isDirty;

            if (index === -1) {
                commit("setTabs",
                    [...state.tabs, {name, extension, persistent, path, flow}]
                );
                isDirty = false;
            } else {
                isDirty = state.tabs[index].dirty;
            }

            commit("setCurrentTab", {
                name,
                extension,
                persistent,
                dirty: isDirty,
                path,
                flow
            });
        },
        closeTab({commit, state}, payload) {
            const {name, index, path} = payload;

            commit("setTabs", state.tabs.filter((tab) => {
                if (path) {
                    return tab.path !== path;
                }
                return tab.name !== name;
            }));

            const POSITION = index
                ? index
                : state.tabs.findIndex((tab) => {
                        if (path) {
                            return tab.path === path;
                        }
                        return tab.name === name;
                    });

            if (state.current.name === name) {
                if(POSITION - 1 >= 0){
                    commit("setCurrentTab", state.tabs[POSITION - 1]);
                }else{
                    commit("setCurrentTab", state.tabs[0]);
                }
            }
        }
    },
    mutations: {
        updateOnboarding(state) {
            state.onboarding = true;
        },
        toggleExplorerVisibility(state, isVisible) {
            state.explorerVisible = isVisible ?? !state.explorerVisible;
        },
        closeExplorer(state) {
            state.explorerVisible = false;
        },
        changeExplorerWidth(state, width) {
            state.explorerWidth = width > 40 ? 40 : width < 20 ? 20 : width;
        },
        setTabContent(state, payload) {
            const tab = state.tabs.find((tab) => tab.path === payload.path);
            if(tab){
                tab.content = payload.content;
            }
        },
        setTabs(state, payload) {
            state.tabs = payload;
        },
        setCurrentTab(state, payload) {
            state.current = payload;
        },
        setTabDirty(state, payload) {
            const {name, dirty, path} =
                payload;

            const tabIdxToDirty = state.tabs.findIndex((tab) => {
                if (path) {
                    return tab.path === path;
                }
                return tab.name === name;
            });

            if(state.tabs[tabIdxToDirty]) state.tabs[tabIdxToDirty].dirty = dirty;
            if(state.current) state.current.dirty = dirty;
        },
        closeTabs(state) {
            if (state.tabs[0]) {
                state.tabs = [state.tabs[0]];
            }
        },
        closeAllTabs(state) {
            state.tabs = [];
            state.current = undefined
        },
        reorderTabs(state, {from, to}) {
            const tab = state.tabs.splice(from, 1)[0];
            state.tabs.splice(to, 0, tab);
        },
        changeView(state, view) {
            state.view = view;
        },
        refreshTree(state) {
            state.explorerVisible = true;
            state.treeRefresh = Date.now();
        },
        setTreeData(state, data) {
            state.treeData = data;
        },
    },
};
