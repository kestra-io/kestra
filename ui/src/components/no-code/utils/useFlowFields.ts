import {computed, ComputedRef, onMounted} from "vue"
import {useI18n} from "vue-i18n"
import {useFlowStore, type ParsedFlow} from "../../../stores/flow"
import {usePluginsStore} from "../../../stores/plugins"
import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
import type {FieldValue} from "./types"


// fields displayed on top of the form
const MAIN_KEYS = [
    "id",
    "namespace",
    "description",
    "inputs",
]

// ---

// fields displayed just after the horizontal bar
export const SECTIONS_IDS = [
    "tasks",
    "triggers",
    "errors",
    "finally",
    "afterExecution",
    "outputs",
]

// once all those fields are displayed, the rest of the fields are displayed
// in alphabetical order

export function useFlowFields(flowSource: ComputedRef<string>){
    const flowStore = useFlowStore()
    const pluginsStore = usePluginsStore()

    const {t} = useI18n()

    onMounted(() => {
        pluginsStore.lazyLoadSchemaType({type: "flow"})
    })

    const parsedFlow = computed<ParsedFlow>(() => {
        try {
            return YAML_UTILS.parse<ParsedFlow>(flowSource.value) ?? {}
        } catch (e) {
            console.error("Error parsing flow YAML", e)
            return {}
        }
    })

    const getFieldFromKey = (key:string, translateGroup: string) => ({
        // A single top-level flow property read off ParsedFlow's index signature is always a
        // schema-driven leaf or branch value, never anything an index signature couldn't describe.
        modelValue: parsedFlow.value[key] as FieldValue | undefined,
        required: pluginsStore.flowRootSchema?.required ?? [],
        disabled: !flowStore.isCreating && (key === "id" || key === "namespace"),
        schema: pluginsStore.flowRootProperties?.[key] ?? {},
        definitions: pluginsStore.flowDefinitions,
        label: SECTIONS_IDS.includes(key) ? key : t(`no_code.fields.${translateGroup}.${key}`),
        fieldKey: key,
        task: parsedFlow.value,
    })

    const fieldsFromSchemaTop = computed(() => MAIN_KEYS.map(key => getFieldFromKey(key, "main")))

    const fieldsFromSchemaRest = computed(() => {
        return Object.keys(pluginsStore.flowRootProperties ?? {})
            .filter((key) => !MAIN_KEYS.includes(key) && (!pluginsStore.flowRootProperties?.[key]?.$deprecated || parsedFlow.value[key]))
            .map((key) => getFieldFromKey(key, "general")).sort((a, b) => {
                const indexA = SECTIONS_IDS.indexOf(a.fieldKey as typeof SECTIONS_IDS[number])
                const indexB = SECTIONS_IDS.indexOf(b.fieldKey as typeof SECTIONS_IDS[number])
                if(indexA === -1 || indexB === -1) {
                    return indexB - indexA
                }
                return indexA - indexB
            })
    })

    return {
        fieldsFromSchemaTop,
        fieldsFromSchemaRest,
        parsedFlow,
    }
}