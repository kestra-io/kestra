import {ref} from "vue";
import {defineStore} from "pinia";

import {useAxios} from "../utils/axios";
import {apiUrl} from "override/utils/route";

import {useMiscStore} from "override/stores/misc";

import {trackBlueprintSelection} from "../utils/tabTracking";
import {Input} from "./flow.ts";

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

export type TemplateArgument = Record<string, Input>;

export interface BlueprintTemplate {
    source: string;
    templateArguments: Record<string, Input>;
}

export interface FlowBlueprint {
    id: string,
    title: string,
    description: string,
    includedTasks?: string[],
    tags?: string[],
    source: string,
    publishedAt?: string,
    template?: BlueprintTemplate
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

    const validateYAML = ref<boolean>(true); // Used to enable/disable YAML validation in Monaco editor, for the purpose of Templated Blueprints

    const getBlueprints = async (options: Options) => {
        const PARAMS = {params: options.params, ...VALIDATE};

        const COMMUNITY = `${API_URL}/blueprints/kinds/${options.kind}/versions/${version}${edition === "OSS" ? "?ee=false" : ""}`;
        const CUSTOM = `${apiUrl()}/blueprints/${options.type}`;

        const response = await axios.get(options.type === "community" ? COMMUNITY : CUSTOM, PARAMS);

        blueprints.value = response.data;
        return response.data;
    };

    const getBlueprint = async (options: Options) => {
        const COMMUNITY = `${API_URL}/blueprints/kinds/${options.kind}/${options.id}/versions/${version}`;
        const CUSTOM = `${apiUrl()}/blueprints/${options.type}/${options.id}`;

        const response = await axios.get(options.type == "community" ? COMMUNITY : CUSTOM);

        if (response.data?.id) {
            trackBlueprintSelection(response.data.id);
        }

        blueprint.value = response.data;
        return response.data;
    };

    const getBlueprintSource = async (options: Options) => {
        const COMMUNITY = `${API_URL}/blueprints/kinds/${options.kind}/${options.id}/versions/${version}/source`;
        const CUSTOM = `${apiUrl()}/blueprints/${options.type}/${options.id}/source`;

        const response = await axios.get(options.type == "community" ? COMMUNITY : CUSTOM);

        source.value = response.data;
        return response.data;
    };

    const getBlueprintGraph = async (options: Options) => {
        const COMMUNITY = `${API_URL}/blueprints/kinds/${options.kind}/${options.id}/versions/${version}/graph`;
        const CUSTOM = `${apiUrl()}/blueprints/${options.type}/${options.id}/graph`;

        const response = await axios.get(options.type == "community" ? COMMUNITY : CUSTOM);

        graph.value = response.data;
        return response.data;
    };

    const getBlueprintTags = async (options: Options) => {
        const PARAMS = {params: options.params, ...VALIDATE};

        const COMMUNITY = `${API_URL}/blueprints/kinds/${options.kind}/versions/${version}/tags`;
        const CUSTOM = `${apiUrl()}/blueprints/${options.type}/tags`;

        const response = await axios.get(options.type == "community" ? COMMUNITY : CUSTOM, PARAMS);

        return response.data;
    };

    const getFlowBlueprint = async (id: string): Promise<FlowBlueprint> => {
        const url = `${apiUrl()}/blueprints/flow/${id}`;

        const response = await axios.get(url);

        if (response.data?.id) {
            trackBlueprintSelection(response.data.id);
        }

        blueprint.value = response.data;
        return response.data;
    };

    const createFlowBlueprint = async (toCreate: {source: string, title: string, description: string, tags: string[]}): Promise<FlowBlueprint> => {
        const url = `${apiUrl()}/blueprints/flows`;
        const body = {
            ...toCreate
        }
        const response = await axios.post(url, body);

        return response.data;
    };

    const updateFlowBlueprint = async (id: string, toUpdate: {source: string, title: string, description: string, tags: string[]}) :Promise<FlowBlueprint> => {
        const url = `${apiUrl()}/blueprints/flows/${id}`;
        const body = {
            ...toUpdate
        }
        const response = await axios.put(url, body);

        return response.data;
    };

    const deleteFlowBlueprint = async (idToDelete: string) => {
        const url = `${apiUrl()}/blueprints/flows/${idToDelete}`;
        await axios.delete(url);
    };

    const useFlowBlueprintTemplate = async (id: string, inputs: Record<string, object>): Promise<{generatedFlowSource: string}> => {
        const url = `${apiUrl()}/blueprints/flows/${id}/use-template`;
        const body = {
            templateArgumentsInputs: inputs
        }
        const response = await axios.post(url, body);

        return response.data;
    }

    return {
        blueprint,
        blueprints,
        source,
        graph,

        validateYAML,

        getBlueprints,
        getBlueprint,
        getBlueprintSource,
        getBlueprintGraph,
        getBlueprintTags,
        useFlowBlueprintTemplate,
        getFlowBlueprint,
        createFlowBlueprint,
        updateFlowBlueprint,
        deleteFlowBlueprint,
    };
});
