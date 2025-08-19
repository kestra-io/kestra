<template>
    <div class="no-code">
        <div class="p-4">
            <Task
                v-if="creatingTask || editingTask"
                @update-task="(yaml) => emit('updateTask', yaml)"
            />

            <el-form label-position="top" v-else>
                <TaskWrapper :key="v.fieldKey" v-for="(v) in fieldsFromSchemaTop" :merge="shouldMerge(v.schema, v.fieldKey)" :transparent="v.fieldKey === 'inputs'">
                    <template #tasks>
                        <TaskObjectField
                            v-bind="v"
                            @update:model-value="(val) => onTaskUpdateField(v.fieldKey, val)"
                        />
                    </template>
                </TaskWrapper>

                <hr class="my-4">

                <TaskWrapper :key="v.fieldKey" v-for="(v) in fieldsFromSchemaRest" :merge="shouldMerge(v.schema, v.fieldKey)" :transparent="SECTIONS_IDS.includes(v.fieldKey)">
                    <template #tasks>
                        <TaskObjectField
                            v-bind="v"
                            @update:model-value="(val) => onTaskUpdateField(v.fieldKey, val)"
                        />
                    </template>
                </TaskWrapper>
            </el-form>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {onMounted, computed, ref, provide, onActivated} from "vue";
    import {useI18n} from "vue-i18n";

    const {t} = useI18n();

    import {usePluginsStore} from "../../stores/plugins";
    import {useFlowStore} from "../../stores/flow";
    const pluginsStore = usePluginsStore();
    const flowStore = useFlowStore();

    import {
        CLOSE_TASK_FUNCTION_INJECTION_KEY,
        CREATING_TASK_INJECTION_KEY,
        PANEL_INJECTION_KEY, POSITION_INJECTION_KEY,
        REF_PATH_INJECTION_KEY, PARENT_PATH_INJECTION_KEY,
        FLOW_INJECTION_KEY, FIELDNAME_INJECTION_KEY,
        EDITING_TASK_INJECTION_KEY, BLOCK_SCHEMA_PATH_INJECTION_KEY,
        SCHEMA_PATH_INJECTION_KEY
    } from "./injectionKeys";

    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import {removeNullAndUndefined} from "./utils/cleanUp";


    import Task from "./segments/Task.vue";
    import TaskWrapper from "../flows/tasks/TaskWrapper.vue";
    import TaskObjectField from "../flows/tasks/TaskObjectField.vue";

    const emit = defineEmits<{
        (e: "updateTask", yaml: string): void
        (e: "reorder", yaml: string): void
        (e: "createTask", parentPath: string, refPath: number | undefined, position?: "before" | "after"): boolean | void
        (e: "editTask", parentPath: string, refPath?: number): boolean | void
        (e: "closeTask"): boolean | void
    }>()

    const props = withDefaults(
        defineProps<{
            flow: string;
            /**
             * The path of the parent block
             */
            parentPath?: string;
            /**
             * Initial block index when opening
             * a no-code panel from topology
             */
            refPath?: number;
            creatingTask?: boolean;
            editingTask?: boolean;
            position?: "before" | "after";
            blockSchemaPath?: string;
            fieldName?: string | undefined;
        }>(), {
            creatingTask: false,
            editingTask: false,
            position: "after",
            refPath: undefined,
            parentPath: undefined,
            blockSchemaPath: "",
            fieldName: undefined,
        });

    provide(SCHEMA_PATH_INJECTION_KEY, computed(() => pluginsStore.schemaType?.flow.$ref ?? ""));


    const saveEvent = (e: KeyboardEvent) => {
        if (e.type === "keydown" && e.key === "s" && e.ctrlKey) {
            e.preventDefault();
            flowStore.save({
                content: props.flow
            });
        }
    };


    function shouldMerge(schema: any, _key: string): boolean {
        const complexObject = ["object", "array"].includes(schema?.type) || schema?.$ref || schema?.oneOf || schema?.anyOf || schema?.allOf;
        return !complexObject
    }

    onActivated(() => {
        pluginsStore.updateDocumentation();
    });

    function onTaskUpdateField(key: string, val: any) {
        const realValue = val === null || val === undefined ? undefined :
            // allow array to be created with null values (specifically for metadata)
            // metadata do not use a buffer value, so each change needs to be reflected in the code,
            // for TaskKvPair.vue (object) we added the buffer value in the input component
            typeof val === "object" && !Array.isArray(val) ? removeNullAndUndefined(val) :
            val; // Handle null values


        const currentFlow = parsedFlow.value;

        currentFlow[key] = realValue;

        emit("updateTask", YAML_UTILS.stringify(currentFlow));
    }

    document.addEventListener("keydown", saveEvent);

    const creatingFlow = computed(() => {
        return flowStore.isCreating;
    });

    const parsedFlow = computed(() => {
        try {
            return YAML_UTILS.parse(props.flow);
        } catch (e) {
            console.error("Error parsing flow YAML", e);
            return {};
        }
    });

    onMounted(async () => {
        if(pluginsStore.schemaType?.flow) {
            return; // Schema already loaded
        }

        await pluginsStore.loadSchemaType()
    });

    // fields displayed on top of the form
    const MAIN_KEYS = [
        "id",
        "namespace",
        "description",
        "inputs"
    ]

    // ---

    // fields displayed just after the horizontal bar
    const SECTIONS_IDS = [
        "tasks",
        "triggers",
        "errors",
        "finally",
        "afterExecution",
        "pluginDefaults",
    ]

    // once all those fields are displayed, the rest of the fields are displayed
    // in alphabetical order, except the ones in HIDDEN_FIELDS
    const HIDDEN_FIELDS = [
        "deleted",
        "tenantId",
        "revision"
    ];

    const getFieldFromKey = (key:string, translateGroup: string) => ({
        modelValue: parsedFlow.value[key],
        required: pluginsStore.flowRootSchema?.required ?? [],
        disabled: !creatingFlow.value && (key === "id" || key === "namespace"),
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


    const panel = ref()

    provide(FLOW_INJECTION_KEY, computed(() => props.flow));
    provide(PARENT_PATH_INJECTION_KEY, props.parentPath ?? "");
    provide(REF_PATH_INJECTION_KEY, props.refPath);
    provide(PANEL_INJECTION_KEY, panel)
    provide(POSITION_INJECTION_KEY, props.position);
    provide(CREATING_TASK_INJECTION_KEY, props.creatingTask);
    provide(EDITING_TASK_INJECTION_KEY, props.editingTask);
    provide(BLOCK_SCHEMA_PATH_INJECTION_KEY, props.blockSchemaPath);
    provide(FIELDNAME_INJECTION_KEY, props.fieldName);

    provide(CLOSE_TASK_FUNCTION_INJECTION_KEY, () => {
        emit("closeTask")
    })
</script>

<style lang="scss" scoped>
    .no-code {
        height: 100%;
        overflow-y: auto;

        hr {
            margin: 0;
        }
    }
</style>
