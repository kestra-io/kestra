export default {
    namespaced: true,
    state: {
        onboarding: false,
        explorerVisible: false,
        explorerWidth: 20,
        current: undefined,
        tabs: [],
    },
    mutations: {
        updateOnboarding(state) {
            state.onboarding = true;
        },
        toggleExplorerVisibility(state, isVisible) {
            state.explorerVisible = isVisible ?? !state.explorerVisible;
        },
        changeExplorerWidth(state, width) {
            state.explorerWidth = width > 40 ? 40 : width < 20 ? 20 : width;
        },
        changeOpenedTabs(state, payload) {
            const {action, name, extension, index, persistent, dirty, path} =
                payload;

            if (action === "open") {
                const index = state.tabs.findIndex((tab) => {
                    if (path) {
                        return tab.path === path;
                    }
                    return tab.name === name;
                });

                let isDirty;

                if (index === -1) {
                    state.tabs.push({name, extension, persistent, path});
                    isDirty = false;
                } else {
                    isDirty = state.tabs[index].dirty;
                }

                state.current = {
                    name,
                    extension,
                    persistent,
                    dirty: isDirty,
                    path,
                };
            } else if (action === "close") {
                state.tabs = state.tabs.filter((tab) => {
                    if (path) {
                        return tab.path !== path;
                    }
                    return tab.name !== name;
                });
                const POSITION = index
                    ? index
                    : state.tabs.findIndex((tab) => {
                          if (path) {
                              return tab.path === path;
                          }
                          return tab.name === name;
                      });

                if (state.current.name === name) {
                    const i = POSITION - 1 >= 0;
                    state.current = i
                        ? state.tabs[POSITION - 1]
                        : state.tabs[0];
                }
            } else if (action === "dirty") {
                const tabIdxToDirty = state.tabs.findIndex((tab) => {
                    if (path) {
                        return tab.path === path;
                    }
                    return tab.name === name;
                });

                state.tabs[tabIdxToDirty].dirty = dirty;
                state.current.dirty = dirty;
            }
        },
        closeTabs(state) {
            if (state.tabs[0]) {
                state.tabs = [state.tabs[0]];
            }
        },
    },
};
