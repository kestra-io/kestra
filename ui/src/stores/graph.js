export default {
    namespaced: true,
    state: {
        node: undefined,
        configurationPanelPosition: undefined,
    },
    actions: {
        updateConfigurationPanelPosition({commit, state}) {
            if (state.node) {
                const position = {
                    right: state.node.getCTM().e + 95,
                    top: state.node.getCTM().f + 10
                }
                commit("setConfigurationPanelPosition", position)
            }
        },
        setNode({commit}, node) {
            commit("setNode", node)
            // dispatch('updateConfigurationPanelPosition')
        },
    },
    mutations: {
        setNode(state, node) {
            state.node = node
        },
        setConfigurationPanelPosition(state, position) {
            state.configurationPanelPosition = position
        }
    }
}
