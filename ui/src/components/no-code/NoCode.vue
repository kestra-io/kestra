<template>
    <div class="no-code" ref="scrollContainer">
        <div class="p-4">
            <Task
                v-if="creatingTask || editingTask"
            />

            <el-form v-else labelPosition="top">
                <Wrapper :key="v.fieldKey" v-for="(v) in fieldsFromSchemaTop" :merge="shouldMerge(v.schema)" :transparent="v.fieldKey === 'inputs'">
                    <template #tasks>
                        <TaskObjectField
                            v-bind="v"
                            @update:model-value="(val) => onTaskUpdateField(v.fieldKey, val)"
                        />
                    </template>
                </Wrapper>

                <hr class="my-4">

                <Wrapper :key="v.fieldKey" v-for="(v) in fieldsFromSchemaRest" :transparent="SECTIONS_IDS.includes(v.fieldKey)">
                    <template #tasks>
                        <TaskObjectField
                            v-bind="v"
                            @update:model-value="(val) => onTaskUpdateField(v.fieldKey, val)"
                        />
                    </template>
                </Wrapper>
            </el-form>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, onActivated, provide, ref, watch} from "vue";

    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import {removeNullAndUndefined} from "./utils/cleanUp";

    import Task from "./segments/Task.vue";
    import Wrapper from "./components/tasks/Wrapper.vue";
    import TaskObjectField from "./components/tasks/TaskObjectField.vue";
    import {
        BLOCK_SCHEMA_PATH_INJECTION_KEY,
        CLOSE_TASK_FUNCTION_INJECTION_KEY,
        CREATE_TASK_FUNCTION_INJECTION_KEY,
        CREATING_FLOW_INJECTION_KEY,
        CREATING_TASK_INJECTION_KEY,
        DEFAULT_NAMESPACE_INJECTION_KEY,
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
        UPDATE_YAML_FUNCTION_INJECTION_KEY,
    } from "./injectionKeys";
    import {useFlowFields, SECTIONS_IDS} from "./utils/useFlowFields";
    import debounce from "lodash/debounce";
    import {NoCodeProps} from "../flows/noCodeTypes";
    import {useFlowStore} from "../../stores/flow";
    import {usePluginsStore} from "../../stores/plugins";
    import {useKeyboardSave} from "./utils/useKeyboardSave";
    import {deepEqual} from "../../utils/utils";
    import {useScrollMemory} from "../../composables/useScrollMemory";
    import {defaultNamespace} from "../../composables/useNamespaces";


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
        

        editorUpdate(YAML_UTILS.replaceBlockWithPath({
            source: flowStore.flowYaml ?? "",
            path: key,
            newContent: YAML_UTILS.stringify(realValue),
        }));
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
    } = useFlowFields(lastValidFlowYaml)

    useKeyboardSave()

    const flowStore = useFlowStore();
    const flowYaml = computed<string>(() => flowStore.flowYaml ?? "");

    const validateFlow = debounce(() => {
        flowStore.validateFlow({flow: flowYaml.value});
    }, 500);

    const timeout = ref();

    const editorUpdate = (source: string) => {
        let parsedSource: any = {}
        try {
            parsedSource = YAML_UTILS.parse(source);
        } catch {
            // ignore parse errors here
            return;
        }
        
        // if no-code would not change the structure of the flow,
        // do not trigger an update as it would remove all formatting and comments
        if(deepEqual(parsedSource, flowStore.flowParsed)) {
            return;
        }
        flowStore.flowYaml = source;
        validateFlow();

        // throttle the trigger of the flow update
        clearTimeout(timeout.value);
        timeout.value = setTimeout(() => {
            flowStore.onEdit({
                source,
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
    provide(CREATING_FLOW_INJECTION_KEY, flowStore.isCreating ?? false);
    provide(DEFAULT_NAMESPACE_INJECTION_KEY, computed(() => flowStore.flow?.namespace ?? defaultNamespace() ?? "company.team"));
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

    provide(UPDATE_YAML_FUNCTION_INJECTION_KEY, (yaml) => {
        editorUpdate(yaml)
    })

    provide(CREATE_TASK_FUNCTION_INJECTION_KEY, (parentPath, blockSchemaPath, refPath) => {
        emit("createTask", parentPath, blockSchemaPath, refPath, "after")
    })

    provide(EDIT_TASK_FUNCTION_INJECTION_KEY, ( parentPath, blockSchemaPath, refPath) => {
        emit("editTask", parentPath, blockSchemaPath, refPath)
    })

    // Scroll position persistence for No-code editor
    const scrollContainer = ref<HTMLDivElement | null>(null);

    const flowIdentity = computed(() => {
        const namespace = flowStore.flow?.namespace ?? "";
        const flowId = flowStore.flow?.id ?? "";
        return `${namespace}/${flowId}`;
    });

    const scrollKey = computed(() => {
        const base = `nocode:${flowIdentity.value}`;
        // home screen
        if (!props.creatingTask && !props.editingTask) return `${base}:home`;
        // task-specific
        const action = props.creatingTask ? "create" : "edit";
        const parentPath = props.parentPath ?? "";
        const refPath = props.refPath ?? "";
        const fieldName = props.fieldName ?? "";
        return `${base}:task:${action}:parentPath:${parentPath}:refPath:${refPath}:fieldName:${fieldName}`;
    });

    useScrollMemory(scrollKey, scrollContainer);

</script>

<style scoped lang="scss">
    .no-code {
        height: 100%;
        overflow-y: auto;

        hr {
            margin: 0;
        }
    }
</style>
