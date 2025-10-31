import {computed, ComputedRef, onMounted} from "vue";
import {useI18n} from "vue-i18n";
import {useFlowStore} from "../../../stores/flow";
import {usePluginsStore} from "../../../stores/plugins";
import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";


// fields displayed on top of the form
const MAIN_KEYS = [
    "id",
    "namespace",
    "description",
    "inputs"
]

// ---

// fields displayed just after the horizontal bar
export const SECTIONS_IDS = [
    "tasks",
    "triggers",
    "errors",
    "finally",
    "afterExecution",
    "pluginDefaults",
    "outputs",
]

// once all those fields are displayed, the rest of the fields are displayed
// in alphabetical order, except the ones in HIDDEN_FIELDS
const HIDDEN_FIELDS = [
    "deleted",
    "tenantId",
    "revision"
];

export function useFlowFields(flowSource: ComputedRef<string>){
    const flowStore = useFlowStore();
    const pluginsStore = usePluginsStore();

    const {t} = useI18n();

    onMounted(() => {
        pluginsStore.lazyLoadSchemaType({type: "flow"});
    });

    const parsedFlow = computed(() => {
        try {
            return YAML_UTILS.parse(flowSource.value) ?? {};
        } catch (e) {
            console.error("Error parsing flow YAML", e);
            return {};
        }
    });

    const getFieldFromKey = (key:string, translateGroup: string) => ({
        modelValue: parsedFlow.value[key],
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
            .filter((key) => !MAIN_KEYS.includes(key) && !HIDDEN_FIELDS.includes(key))
            .map((key) => getFieldFromKey(key, "general")).sort((a, b) => {
                const indexA = SECTIONS_IDS.indexOf(a.fieldKey as typeof SECTIONS_IDS[number]);
                const indexB = SECTIONS_IDS.indexOf(b.fieldKey as typeof SECTIONS_IDS[number]);
                if(indexA === -1 || indexB === -1) {
                    return indexB - indexA;
                }
                return indexA - indexB;
            });
    });

    return {
        fieldsFromSchemaTop,
        fieldsFromSchemaRest,
        parsedFlow,
    }
}