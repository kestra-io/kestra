import {usePluginsStore} from "../../../stores/plugins";
import {computed, onMounted} from "vue";
import {useDashboardStore} from "../../../stores/dashboard";

const FIELD_ORDER = [
    "id",
    "title",
    "description",
    "timeWindow",
    "charts"
]

const HIDDEN_FIELDS = [
    "deleted",
    "tenantId",
    "created",
    "updated",
    "sourceCode",
];

export function useDashboardFields() {
    const pluginsStore = usePluginsStore();
    const dashboardStore = useDashboardStore();

    onMounted(() => {
        pluginsStore.lazyLoadSchemaType({type: "dashboard"});
    });

    const parsedSource = computed(() => dashboardStore.parsedSource)

    const getFieldFromKey = (key:string) => ({
        modelValue: parsedSource.value?.[key],
        disabled: !dashboardStore.isCreating && (key === "id"),
        required: dashboardStore.rootSchema?.required ?? [],
        schema: dashboardStore.rootProperties?.[key] ?? {},
        definitions: dashboardStore.definitions,
        label: key,
        fieldKey: key,
        task: parsedSource.value,
    })

    const fieldsFromSchema = computed(() => {
        return Object.keys(dashboardStore.rootProperties ?? {})
                    .filter((key) => !HIDDEN_FIELDS.includes(key))
                    .map((key) => getFieldFromKey(key))
                    // sort so the fields in field order appear first and the rest after
                    .sort((a, b) => {
                        const aIndex = FIELD_ORDER.indexOf(a.fieldKey);
                        const bIndex = FIELD_ORDER.indexOf(b.fieldKey);
                        if (aIndex === -1 && bIndex === -1) return 0;
                        if (aIndex === -1) return 1;
                        if (bIndex === -1) return -1;
                        return aIndex - bIndex;
                    })
                })

    return {
        fieldsFromSchema,
        parsedSource
    }
}
