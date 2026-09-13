import {computed, ref} from "vue"
import {defineStore} from "pinia"

import {useClient, type BlueprintControllerApiBlueprintItemWithSource} from "@kestra-io/kestra-sdk"
import {apiUrl} from "override/utils/route"

import {useMiscStore} from "override/stores/misc"

import {trackBlueprintSelection} from "../utils/tabTracking"
import {Input} from "./flow.ts"

export type BlueprintType = "community" | "custom";
type BlueprintKind = "flow" | "dashboard" | "app";

interface Options {
    type: BlueprintType;
    kind?: BlueprintKind;
    id?: string;
    params?: Record<string, any>;
}

interface Blueprint {
    id?: string;
}

export type TemplateArgument = Record<string, Input>;

// The custom / "flow" blueprint endpoints (create / update / delete / use-template and the
// `/blueprints/custom` + `/blueprints/flow` reads) are an EE-only feature — the OSS backend's
// blueprint controller serves only community blueprints (via api.kestra.io), so these have no entry
// in the OSS SDK. They are called through the raw fetch client against apiUrl() (exactly like
// getBlueprintGraph already does), which compiles identically for OSS and EE and reaches the EE
// backend at runtime. This shape extends the generated blueprint DTO with the extra fields the UI
// reads that only exist for flow blueprints.
export type FlowBlueprint = BlueprintControllerApiBlueprintItemWithSource & {
    // A flow blueprint may carry a template definition (EE feature): its source plus the arguments
    // the UI renders as inputs when instantiating it. Kept structural (not tied to an EE-only SDK
    // type) so the shared store compiles against the OSS SDK too.
    template?: { source?: string; templateArguments?: Record<string, unknown> };
    includedFlows?: string[];
};

export interface BlueprintTag {
    id: string;
    name: string;
}

const API_URL = "https://api.kestra.io/v1"
const VALIDATE = {validateStatus: (status: number) => status === 200 || status === 401}

export const useBlueprintsStore = defineStore("blueprints", () => {
    const axios = useClient()

    const miscStore = useMiscStore()
    const {edition, version} = miscStore.configs || {}

    const blueprint = ref<Blueprint | undefined>(undefined)

    const validateYAML = ref<boolean>(true) // Used to enable/disable YAML validation in Monaco editor, for the purpose of Templated Blueprints

    const validation = ref<{constraints?: string} | undefined>(undefined)

    const validationErrors = computed<string[] | undefined>(
        () => validation.value?.constraints ? [validation.value.constraints] : undefined,
    )

    const getBlueprints = async (options: Options) => {
        if (options.type === "community") {
            const PARAMS = {params: options.params, ...VALIDATE}
            const COMMUNITY = `${API_URL}/blueprints/kinds/${options.kind}/versions/${version}${edition === "OSS" ? "?ee=false" : ""}`
            const response = await axios.get(COMMUNITY, PARAMS)
            return response.data
        }

        try {
            const {data} = await axios.get(`${apiUrl()}/blueprints/custom`, {params: toCustomBlueprintParams(options.params)})
            return data
        } catch (e: any) {
            if (e.status === 401) return {results: [], total: 0}
            throw e
        }
    }

    function toCustomBlueprintParams(params?: Record<string, any>) {
        const {q, tags, ...rest} = params ?? {}
        const converted: Record<string, any> = {...rest}
        if (q !== undefined && q !== null) {
            converted["filters[q][EQUALS]"] = q
        }
        if (tags !== undefined && tags !== null) {
            converted["filters[tags][IN]"] = Array.isArray(tags) ? tags.join(",") : tags
        }
        return converted
    }

    const getBlueprint = async (options: Options) => {
        if (options.type === "community") {
            const COMMUNITY = `${API_URL}/blueprints/kinds/${options.kind}/${options.id}/versions/${version}`
            const response = await axios.get(COMMUNITY)
            if (response.data?.id) {
                trackBlueprintSelection(response.data.id)
            }
            blueprint.value = response.data
            return response.data
        }

        const {data} = await axios.get(`${apiUrl()}/blueprints/custom/${options.id!}`)
        const blueprintData = data as unknown as Blueprint
        if (blueprintData?.id) {
            trackBlueprintSelection(blueprintData.id)
        }
        blueprint.value = blueprintData
        return blueprintData
    }

    const getBlueprintSource = async (options: Options) => {
        if (options.type === "community") {
            const COMMUNITY = `${API_URL}/blueprints/kinds/${options.kind}/${options.id}/versions/${version}/source`
            const response = await axios.get(COMMUNITY)
            return response.data
        }

        const {data} = await axios.get(`${apiUrl()}/blueprints/custom/${options.id!}/source`)
        return data
    }

    const getBlueprintGraph = async (options: Options) => {
        const COMMUNITY = `${API_URL}/blueprints/kinds/${options.kind}/${options.id}/versions/${version}/graph`
        const CUSTOM = `${apiUrl()}/blueprints/${options.type}/${options.id}/graph`

        const response = await axios.get(options.type == "community" ? COMMUNITY : CUSTOM)

        return response.data
    }

    const getBlueprintTags = async (options: Options) => {
        if (options.type === "community") {
            const PARAMS = {params: options.params, ...VALIDATE}
            const COMMUNITY = `${API_URL}/blueprints/kinds/${options.kind}/versions/${version}/tags`
            const response = await axios.get(COMMUNITY, PARAMS)
            return response.data
        }

        try {
            const {data} = await axios.get(`${apiUrl()}/blueprints/custom/tags`, {params: toCustomBlueprintParams(options.params)})
            return data
        } catch (e: any) {
            if (e.status === 401) return []
            throw e
        }
    }

    const getFlowBlueprint = async (id: string): Promise<FlowBlueprint> => {
        const {data} = await axios.get<FlowBlueprint>(`${apiUrl()}/blueprints/flow/${id}`)

        if (data?.id) {
            trackBlueprintSelection(data.id)
        }

        blueprint.value = data
        return data
    }

    const createFlowBlueprint = async (toCreate: {source: string, title: string, description: string, tags: string[]}) => {
        const {data} = await axios.post<FlowBlueprint>(`${apiUrl()}/blueprints/flows`, toCreate)
        return data
    }

    const updateFlowBlueprint = async (id: string, toUpdate: {source: string, title: string, description: string, tags: string[]}) => {
        const {data} = await axios.put<FlowBlueprint>(`${apiUrl()}/blueprints/flows/${id}`, toUpdate)
        return data
    }

    const validateFlowBlueprint = async (source: string): Promise<void> => {
        const {data} = await axios.post<{constraints?: string}>(`${apiUrl()}/blueprints/flows/validate`, source, {
            headers: {"Content-Type": "application/x-yaml"},
            showMessageOnError: false,
        })
        validation.value = data
    }

    const deleteFlowBlueprint = async (idToDelete: string) => {
        await axios.delete(`${apiUrl()}/blueprints/flows/${idToDelete}`)
    }

    const useFlowBlueprintTemplate = async (id: string, inputs: Record<string, object>): Promise<{generatedFlowSource: string}> => {
        const {data} = await axios.post<{generatedFlowSource: string}>(`${apiUrl()}/blueprints/flows/${id}/use-template`, {templateArgumentsInputs: inputs})
        return data
    }

    return {
        blueprint,
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
        validation,
        validationErrors,
        validateFlowBlueprint,
        deleteFlowBlueprint,
    }
})
