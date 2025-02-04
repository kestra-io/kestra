import {apiUrl} from "override/utils/route";
export default {
    namespaced: true,
    state: {
        blueprint: undefined,
        blueprints: [],
        source: undefined,
        graph: undefined
    },

    actions: {
        getBlueprint({commit}, options) {
            const kind = options.kind && options.type !== "custom" ? `/${options.kind}` : "";
            return this.$http.get(
                `${apiUrl(this)}/blueprints/${options.type}${kind}/${options.id}`
            )
                .then(response => {
                    commit("setBlueprint", response.data)
                    return response.data;
                });
        },
        getBlueprintSource({commit}, options) {
            const kind = options.kind && options.type !== "custom" ? `/${options.kind}` : "";
            return this.$http.get(
                `${apiUrl(this)}/blueprints/${options.type}${kind}/${options.id}/source`
            )
            .then(response => {
                commit("setSource", response.data)
                return response.data;
            });
        },
        getBlueprintGraph({commit}, options) {
            const kind = options.kind && options.type !== "custom" ? `/${options.kind}` : "";
            return this.$http.get(
                `${apiUrl(this)}/blueprints/${options.type}${kind}/${options.id}/graph`
            )
            .then(response => {
                commit("setGraph", response.data)
                return response.data;
            });
        },
        getBlueprintsForQuery({commit}, options) {
            const kind = options.kind && options.type !== "custom" ? `/${options.kind}` : "";
            return this.$http.get(
                `${apiUrl(this)}/blueprints/${options.type}${kind}`,
                {params: options.params}
                )
                .then(response => {
                    commit("setBlueprints", response.data)
                    return response.data;
                });
        },
        getBlueprintTagsForQuery(_, options) {
            const kind = options.kind && options.type !== "custom" ? `/${options.kind}` : "";
            return this.$http.get(
                `${apiUrl(this)}/blueprints/${options.type}${kind}/tags`,
                {params: options.params}
            )
            .then(response => {
                return response.data;
            });
        },
    },
    mutations: {
        setBlueprints(state, blueprints) {
            state.blueprints = blueprints
        },
        setBlueprint(state, blueprint) {
            state.blueprint = blueprint
        },
        setSource(state, source) {
            state.source = source
        },
        setGraph(state, graph) {
            state.graph = graph
        },
    },
    getters: {
    }
}
