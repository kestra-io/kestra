<template>
    <div class="no-code">
        <div class="p-4">
            <Task
                v-if="creatingTask || editingTask"
            />

            <el-form v-else label-position="top">
                <TaskWrapper :key="v.fieldKey" v-for="(v) in fieldsFromSchemaTop" :merge="shouldMerge(v.schema)" :transparent="v.fieldKey === 'inputs'">
                    <template #tasks>
                        <TaskObjectField
                            v-bind="v"
                            @update:model-value="(val) => onTaskUpdateField(v.fieldKey, val)"
                        />
                    </template>
                </TaskWrapper>

                <hr class="my-4">

                <TaskWrapper :key="v.fieldKey" v-for="(v) in fieldsFromSchemaRest" :merge="shouldMerge(v.schema)" :transparent="SECTIONS_IDS.includes(v.fieldKey)">
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
    import {computed, onActivated, provide, ref, watch} from "vue";

    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import {removeNullAndUndefined} from "./utils/cleanUp";

    import Task from "./segments/Task.vue";
    import TaskWrapper from "../flows/tasks/TaskWrapper.vue";
    import TaskObjectField from "../flows/tasks/TaskObjectField.vue";
    import {
        BLOCK_SCHEMA_PATH_INJECTION_KEY,
        CLOSE_TASK_FUNCTION_INJECTION_KEY,
        CREATE_TASK_FUNCTION_INJECTION_KEY,
        CREATING_TASK_INJECTION_KEY,
        EDIT_TASK_FUNCTION_INJECTION_KEY,
        EDITING_TASK_INJECTION_KEY,
        FIELDNAME_INJECTION_KEY,
        FULL_SCHEMA_INJECTION_KEY,
        FULL_SOURCE_INJECTION_KEY,
        PANEL_INJECTION_KEY,
        PARENT_PATH_INJECTION_KEY,
        POSITION_INJECTION_KEY,
        REF_PATH_INJECTION_KEY,
        ROOT_SCHEMA_INJECTION_KEY,
        SCHEMA_DEFINITIONS_INJECTION_KEY,
        UPDATE_TASK_FUNCTION_INJECTION_KEY,
    } from "./injectionKeys";
    import {useFlowFields, SECTIONS_IDS} from "./utils/useFlowFields";
    import {debounce} from "lodash";
    import {useEditorStore} from "../../stores/editor";
    import {useFlowStore} from "../../stores/flow";
    import {usePluginsStore} from "../../stores/plugins";
    import {useKeyboardSave} from "./utils/useKeyboardSave";
    import {NoCodeProps} from "../flows/noCodeTypes";


    const props = defineProps<NoCodeProps>();

    function shouldMerge(schema: any): boolean {
        const complexObject = ["object", "array"].includes(schema?.type) || schema?.$ref || schema?.oneOf || schema?.anyOf || schema?.allOf;
        return !complexObject
    }

    function onTaskUpdateField(key: string, val: any) {
        const realValue = val === null || val === undefined ? undefined :
            // allow array to be created with null values (specifically for metadata)
            // metadata do not use a buffer value, so each change needs to be reflected in the code,
            // for TaskKvPair.vue (object) we added the buffer value in the input component
            typeof val === "object" && !Array.isArray(val)
                ? removeNullAndUndefined(val)
                : val; // Handle null values


        const currentFlow = parsedFlow.value;

        currentFlow[key] = realValue;

        editorUpdate(YAML_UTILS.stringify(currentFlow));
    }

    const lastValidFlowYaml = computed<string>(
        (oldValue) => {
            try {
                YAML_UTILS.parse(flowYaml.value);
                return flowYaml.value;
            } catch {
                return oldValue ?? "";
            }
        }
    );

    const {
        fieldsFromSchemaTop,
        fieldsFromSchemaRest,
        parsedFlow,
    } = useFlowFields(lastValidFlowYaml)

    useKeyboardSave(lastValidFlowYaml)

    const flowStore = useFlowStore();
    const flowYaml = computed<string>(() => flowStore.flowYaml ?? "");

    const validateFlow = debounce(() => {
        flowStore.validateFlow({flow: flowYaml.value});
    }, 500);

    const timeout = ref();
    const editorStore = useEditorStore();

    const editorUpdate = (source: string) => {
        flowStore.flowYaml = source;
        flowStore.haveChange = true;
        validateFlow();
        editorStore.setTabDirty({
            name: "Flow",
            dirty: true
        });

        // throttle the trigger of the flow update
        clearTimeout(timeout.value);
        timeout.value = setTimeout(() => {
            flowStore.onEdit({
                source,
                currentIsFlow: true,
                topologyVisible: true,
            });
        }, 1000);
    };

    onActivated(() => {
        pluginsStore.updateDocumentation();
    });

    watch(
        () => flowStore.flowYaml,
        (newVal, oldVal) => {
            if (newVal !== oldVal) {
                editorUpdate(newVal);
            }
        }
    );

    const panel = ref()
    const pluginsStore = usePluginsStore();

    provide(FULL_SOURCE_INJECTION_KEY, computed(() => lastValidFlowYaml.value));
    provide(PARENT_PATH_INJECTION_KEY, props.parentPath ?? "");
    provide(REF_PATH_INJECTION_KEY, props.refPath);
    provide(PANEL_INJECTION_KEY, panel)
    provide(POSITION_INJECTION_KEY, props.position ?? "after");
    provide(CREATING_TASK_INJECTION_KEY, props.creatingTask);
    provide(EDITING_TASK_INJECTION_KEY, props.editingTask);
    provide(FIELDNAME_INJECTION_KEY, props.fieldName);
    provide(BLOCK_SCHEMA_PATH_INJECTION_KEY, computed(() => props.blockSchemaPath ?? pluginsStore.flowSchema?.$ref ?? ""));
    provide(FULL_SCHEMA_INJECTION_KEY, computed(() => pluginsStore.flowSchema ?? {}));
    provide(ROOT_SCHEMA_INJECTION_KEY, computed(() => pluginsStore.flowRootSchema ?? {}));
    provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => pluginsStore.flowDefinitions ?? {}));

    const emit = defineEmits<{
        (e: "createTask", parentPath: string, blockSchemaPath: string, refPath: number | undefined,  position: "after" | "before"): boolean | void;
        (e: "editTask", parentPath: string, blockSchemaPath: string, refPath: number | undefined): boolean | void;
        (e: "closeTask"): boolean | void;
    }>();

    provide(CLOSE_TASK_FUNCTION_INJECTION_KEY, () => {
        emit("closeTask")
    })

    provide(UPDATE_TASK_FUNCTION_INJECTION_KEY, (yaml) => {
        editorUpdate(yaml)
    })

    provide(CREATE_TASK_FUNCTION_INJECTION_KEY, (parentPath, blockSchemaPath, refPath) => {
        emit("createTask", parentPath, blockSchemaPath, refPath, "after")
    });

    provide(EDIT_TASK_FUNCTION_INJECTION_KEY, ( parentPath, blockSchemaPath, refPath) => {
        emit("editTask", parentPath, blockSchemaPath, refPath)
    });


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
