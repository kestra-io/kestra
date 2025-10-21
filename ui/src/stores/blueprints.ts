import {ref} from "vue";
import {defineStore} from "pinia";

import {useAxios} from "../utils/axios";

import {useMiscStore} from "override/stores/misc";

import {trackBlueprintSelection} from "../utils/tabTracking";

export type Type = "community" | "custom";
export type Kind = "flow" | "dashboard" | "app";

interface Options {
    type: Type;

    kind?: Kind;
    id?: string;
    params?: Record<string, any>;
}

interface Blueprint {
    id: string;
    [key: string]: any;
}

const API_URL = "https://api.kestra.io/v1";
const VALIDATE = {validateStatus: (status: number) => status === 200 || status === 401};

const getKind = ({kind, type}: Options) => kind && type !== "custom" ? kind : "";

export const useBlueprintsStore = defineStore("blueprints", () => {
    const axios = useAxios();

    const miscStore = useMiscStore();
    const {edition, version} = miscStore.configs || {};

    const blueprints = ref<Blueprint[]>([]);
    const blueprint = ref<Blueprint | undefined>(undefined);
    const source = ref<string | undefined>(undefined);
    const graph = ref<any | undefined>(undefined);

    const getBlueprints = async (options: Options) => {
        const response = await axios.get(`${API_URL}/blueprints/kinds/${getKind(options)}/versions/${version}${edition === "OSS" ? "?ee=false" : ""}`, {params: options.params, ...VALIDATE});

        blueprints.value = response.data;
        return response.data;
    };

    const getBlueprint = async (options: Options) => {
        const response = await axios.get(`${API_URL}/blueprints/kinds/${getKind(options)}/${options.id}/versions/${version}`);

        if (response.data?.id) {
            trackBlueprintSelection(response.data.id);
        }

        blueprint.value = response.data;
        return response.data;
    };

    const getBlueprintSource = async (options: Options) => {
        const response = await axios.get(`${API_URL}/blueprints/kinds/${getKind(options)}/${options.id}/versions/${version}/source`);

        source.value = response.data;
        return response.data;
    };

    const getBlueprintGraph = async (options: Options) => {
        const response = await axios.get(`${API_URL}/blueprints/kinds/${getKind(options)}/${options.id}/versions/${version}/graph`);

        graph.value = response.data;
        return response.data;
    };

    const getBlueprintTags = async (options: Options) => {
        const response = await axios.get(`${API_URL}/blueprints/kinds/${getKind(options)}/versions/${version}/tags`, {params: options.params, ...VALIDATE});

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
