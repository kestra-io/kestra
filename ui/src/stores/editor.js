export default {
    namespaced: true,
    state: {
        explorerVisible: true,
        current: undefined,
        tabs: [],
    },
    mutations: {
        toggleExplorerVisibility(state) {
            state.explorerVisible = !state.explorerVisible;
        },
        changeOpenedTabs(state, payload) {
            const {
                action,
                name,
                extension,
                index,
                persistent,
                local,
                dirty,
                path,
            } = payload;

            if (action === "open") {
                const index = state.tabs.findIndex((tab) => tab.name === name);

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
                    local,
                    path,
                };
            } else if (action === "close") {
                state.tabs = state.tabs.filter((tab) => tab.name !== name);
                const POSITION = index
                    ? index
                    : state.tabs.findIndex((tab) => tab.name !== name);

                if (state.current.name === name) {
                    const i = POSITION - 1 >= 0;
                    state.current = i
                        ? state.tabs[POSITION - 1]
                        : state.tabs[0];
                }
            } else if (action === "dirty") {
                state.tabs.map((tab) => {
                    if (tab.name === name) tab.dirty = dirty;
                });

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
