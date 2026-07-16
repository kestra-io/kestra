import {ref} from "vue"
import {defineStore} from "pinia"

import {BlueprintControllerApiFlowBlueprint, useClient} from "@kestra-io/kestra-sdk"
import {apiUrl} from "override/utils/route"
import * as BlueprintsAPI from "@kestra-io/kestra-sdk/blueprints"
import * as BlueprintTagsAPI from "@kestra-io/kestra-sdk/blueprint-tags"
import type {QueryFilter} from "@kestra-io/design-system"

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

export type {BlueprintControllerApiFlowBlueprint as FlowBlueprint}

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

    const blueprints = ref<Blueprint[]>([])
    const blueprint = ref<Blueprint | undefined>(undefined)
    const source = ref<string | undefined>(undefined)
    const graph = ref<any | undefined>(undefined)

    const validateYAML = ref<boolean>(true) // Used to enable/disable YAML validation in Monaco editor, for the purpose of Templated Blueprints

    const getBlueprints = async (options: Options) => {
        if (options.type === "community") {
            const PARAMS = {params: options.params, ...VALIDATE}
            const COMMUNITY = `${API_URL}/blueprints/kinds/${options.kind}/versions/${version}${edition === "OSS" ? "?ee=false" : ""}`
            const response = await axios.get(COMMUNITY, PARAMS)
            blueprints.value = response.data
            return response.data
        }

        try {
            const data = await BlueprintsAPI.searchInternalBlueprints(toCustomBlueprintParams(options.params))
            blueprints.value = data.results as unknown as Blueprint[]
            return data
        } catch (e: any) {
            if (e.status === 401) return {results: [], total: 0}
            throw e
        }
    }

    /**
     * The /blueprints/custom backend reads search/tag filters from the QueryFilter format.
     * Legacy callers still pass `q` / `tags` as scalar params, so translate them into a
     * QueryFilter[] here. The external community API (api.kestra.io) still expects the legacy
     * scalar form, so this is only used for `custom`.
     */
    function toCustomBlueprintParams(params?: Record<string, any>) {
        const {q, tags, ...rest} = params ?? {}
        const filters: QueryFilter[] = []
        if (q !== undefined && q !== null) {
            filters.push({field: "q", operation: "EQUALS", value: q})
        }
        if (tags !== undefined && tags !== null) {
            filters.push({field: "tags", operation: "IN", value: Array.isArray(tags) ? tags.join(",") : tags})
        }
        return {...rest, filters} as Parameters<typeof BlueprintsAPI.searchInternalBlueprints>[0]
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

        const data = await BlueprintsAPI.internalBlueprint({id: options.id!}) as unknown as Blueprint
        if (data?.id) {
            trackBlueprintSelection(data.id)
        }
        blueprint.value = data
        return data
    }

    const getBlueprintSource = async (options: Options) => {
        if (options.type === "community") {
            const COMMUNITY = `${API_URL}/blueprints/kinds/${options.kind}/${options.id}/versions/${version}/source`
            const response = await axios.get(COMMUNITY)
            source.value = response.data
            return response.data
        }

        const data = await BlueprintsAPI.internalBlueprintFlow({id: options.id!})
        source.value = data
        return data
    }

    const getBlueprintGraph = async (options: Options) => {
        const COMMUNITY = `${API_URL}/blueprints/kinds/${options.kind}/${options.id}/versions/${version}/graph`
        const CUSTOM = `${apiUrl()}/blueprints/${options.type}/${options.id}/graph`

        const response = await axios.get(options.type == "community" ? COMMUNITY : CUSTOM)

        graph.value = response.data
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
            return await BlueprintTagsAPI.internalBlueprintTags(toCustomBlueprintParams(options.params) as Parameters<typeof BlueprintTagsAPI.internalBlueprintTags>[0])
        } catch (e: any) {
            if (e.status === 401) return []
            throw e
        }
    }

    const getFlowBlueprint = async (id: string) => {
        const data = await BlueprintsAPI.flowBlueprint({id})

        if (data?.id) {
            trackBlueprintSelection(data.id)
        }

        blueprint.value = data
        return data
    }

    const createFlowBlueprint = async (toCreate: {source: string, title: string, description: string, tags: string[]}) => {
        return BlueprintsAPI.createFlowBlueprint(toCreate)
    }

    const updateFlowBlueprint = async (id: string, toUpdate: {source: string, title: string, description: string, tags: string[]}) => {
        return BlueprintsAPI.updateFlowBlueprint({id, ...toUpdate})
    }

    const deleteFlowBlueprint = async (idToDelete: string) => {
        await BlueprintsAPI.deleteFlowBlueprints({id: idToDelete})
    }

    const useFlowBlueprintTemplate = async (id: string, inputs: Record<string, object>): Promise<{generatedFlowSource: string}> => {
        return BlueprintsAPI.useBlueprintTemplate({id, templateArgumentsInputs: inputs as any}) as Promise<{generatedFlowSource: string}>
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
    }
})
