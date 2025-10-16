import {defineStore} from "pinia";
import {apiUrl} from "override/utils/route";
import {trackBlueprintSelection} from "../utils/tabTracking";

export const VALIDATE = {validateStatus: (status: number) => status === 200 || status === 401};

interface Blueprint {
    [key: string]: any;
}

interface BlueprintOptions {
    kind?: string;
    type: string;
    id?: string;
    params?: Record<string, any>;
}

interface State {
    blueprint: Blueprint | undefined;
    blueprints: Blueprint[];
    source: string | undefined;
    graph: any | undefined;
}

export const useBlueprintsStore = defineStore("blueprints", {
    state: (): State => ({
        blueprint: undefined,
        blueprints: [],
        source: undefined,
        graph: undefined
    }),
    actions: {
        async getBlueprint(options: BlueprintOptions) {
            const kind = options.kind && options.type !== "custom" ? `/${options.kind}` : "";
            const response = await this.$http.get(
                `${apiUrl()}/blueprints/${options.type}${kind}/${options.id}`
            );
            this.blueprint = response.data;

            if (response.data?.id) {
                trackBlueprintSelection(response.data.id);
            }

            return response.data;
        },

        async getBlueprintSource(options: BlueprintOptions) {
            const kind = options.kind && options.type !== "custom" ? `/${options.kind}` : "";
            const response = await this.$http.get(
                `${apiUrl()}/blueprints/${options.type}${kind}/${options.id}/source`
            );
            this.source = response.data;
            return response.data;
        },

        async getBlueprintGraph(options: BlueprintOptions) {
            const kind = options.kind && options.type !== "custom" ? `/${options.kind}` : "";
            const response = await this.$http.get(
                `${apiUrl()}/blueprints/${options.type}${kind}/${options.id}/graph`
            );
            this.graph = response.data;
            return response.data;
        },

        async getBlueprintsForQuery(options: BlueprintOptions) {
            const kind = options.kind && options.type !== "custom" ? `/${options.kind}` : "";
            const response = await this.$http.get(
                `${apiUrl()}/blueprints/${options.type}${kind}`,
                {params: options.params, ...VALIDATE}
            );
            this.blueprints = response.data;
            return response.data;
        },

        async getBlueprintTagsForQuery(options: BlueprintOptions) {
            const kind = options.kind && options.type !== "custom" ? `/${options.kind}` : "";
            const response = await this.$http.get(
                `${apiUrl()}/blueprints/${options.type}${kind}/tags`,
                {params: options.params, ...VALIDATE}
            );
            return response.data;
        },

        setBlueprints(blueprints: Blueprint[]) {
            this.blueprints = blueprints;
        },

        setBlueprint(blueprint: Blueprint) {
            this.blueprint = blueprint;
        },

        setSource(source: string) {
            this.source = source;
        },

        setGraph(graph: any) {
            this.graph = graph;
        }
    }
});
