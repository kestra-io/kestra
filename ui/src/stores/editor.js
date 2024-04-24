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
        changeOpenedTabs(
            state,
            {action, name, extension, index, persistent},
        ) {
            if (action === "open") {
                const index = state.tabs.findIndex((tab) => tab.name === name);
                const data = {name, extension, persistent};

                if (index === -1) state.tabs.push(data);
                state.current = data;
            } else if (action === "close") {
                state.tabs = state.tabs.filter((tab) => tab.name !== name);

                if (state.current.name === name) {
                    const i = index - 1 >= 0;
                    state.current = i ? state.tabs[index - 1] : undefined;
                }
            }
        },
    },
};
