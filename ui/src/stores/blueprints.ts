import {ref} from "vue";
import {defineStore} from "pinia";

import {useAxios} from "../utils/axios";
import {apiUrl} from "override/utils/route";

import {useMiscStore} from "override/stores/misc";

import {trackBlueprintSelection} from "../utils/tabTracking";

export type BlueprintType = "community" | "custom";
type BlueprintKind = "flow" | "dashboard" | "app";

interface Options {
    type: BlueprintType;

    kind?: BlueprintKind;
    id?: string;
    params?: Record<string, any>;
}

interface Blueprint {
    id: string;
    [key: string]: any;
}

const API_URL = "https://api.kestra.io/v1";
const VALIDATE = {validateStatus: (status: number) => status === 200 || status === 401};

export const useBlueprintsStore = defineStore("blueprints", () => {
    const axios = useAxios();

    const miscStore = useMiscStore();
    const {edition, version} = miscStore.configs || {};

    const blueprints = ref<Blueprint[]>([]);
    const blueprint = ref<Blueprint | undefined>(undefined);
    const source = ref<string | undefined>(undefined);
    const graph = ref<any | undefined>(undefined);

    const getBlueprints = async (options: Options) => {
        const PARAMS = {params: options.params, ...VALIDATE};

        const COMMUNITY = `${API_URL}/blueprints/kinds/${options.kind}/versions/${version}${edition === "OSS" ? "?ee=false" : ""}`;
        const CUSTOM = `${apiUrl()}/blueprints/${options.type}${options.kind}`;

        const response = await axios.get(options.type === "community" ? COMMUNITY : CUSTOM, PARAMS);

        blueprints.value = response.data;
        return response.data;
    };

    const getBlueprint = async (options: Options) => {
        const COMMUNITY = `${API_URL}/blueprints/kinds/${options.kind}/${options.id}/versions/${version}`;
        const CUSTOM = `${apiUrl()}/blueprints/${options.type}${options.kind}/${options.id}`;

        const response = await axios.get(options.type == "community" ? COMMUNITY : CUSTOM);

        if (response.data?.id) {
            trackBlueprintSelection(response.data.id);
        }

        blueprint.value = response.data;
        return response.data;
    };

    const getBlueprintSource = async (options: Options) => {
        const COMMUNITY = `${API_URL}/blueprints/kinds/${options.kind}/${options.id}/versions/${version}/source`;
        const CUSTOM = `${apiUrl()}/blueprints/${options.type}${options.kind}/${options.id}/source`;

        const response = await axios.get(options.type == "community" ? COMMUNITY : CUSTOM);

        source.value = response.data;
        return response.data;
    };

    const getBlueprintGraph = async (options: Options) => {
        const COMMUNITY = `${API_URL}/blueprints/kinds/${options.kind}/${options.id}/versions/${version}/graph`;
        const CUSTOM = `${apiUrl()}/blueprints/${options.type}${options.kind}/${options.id}/graph`;

        const response = await axios.get(options.type == "community" ? COMMUNITY : CUSTOM);

        graph.value = response.data;
        return response.data;
    };

    const getBlueprintTags = async (options: Options) => {
        const PARAMS = {params: options.params, ...VALIDATE};

        const COMMUNITY = `${API_URL}/blueprints/kinds/${options.kind}/versions/${version}/tags`;
        const CUSTOM = `${apiUrl()}/blueprints/${options.type}${options.kind}/tags`;

        const response = await axios.get(options.type == "community" ? COMMUNITY : CUSTOM, PARAMS);

        return response.data;
    };

    return {
        blueprint,
        blueprints,
        source,
        graph,

        getBlueprints,
        getBlueprint,
        getBlueprintSource,
        getBlueprintGraph,
        getBlueprintTags,
    };
});
