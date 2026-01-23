<template>
    <div class="no-code">
        <div class="p-4">
            <Task
                v-if="creatingTask || editingTask"
            />

            <el-form v-else labelPosition="top">
                <Wrapper :key="v.fieldKey" v-for="(v) in fieldsFromSchema" :transparent="v.fieldKey === 'inputs'" :merge="shouldMerge(v.schema)">
                    <template #tasks>
                        <TaskObjectField
                            v-bind="v"
                            @update:model-value="(val: any) => onTaskUpdateField(v.fieldKey, val)"
                        />
                    </template>
                </Wrapper>
            </el-form>
        </div>
    </div>
</template>
<script lang="ts" setup>
    import {computed, onActivated, provide} from "vue";
    import Task from "../../no-code/segments/Task.vue";
    import Wrapper from "../../no-code/components/tasks/Wrapper.vue";
    import TaskObjectField from "../../no-code/components/tasks/TaskObjectField.vue";
    import {useDashboardFields} from "../composables/useDashboardFields";
    import {useDashboardStore} from "../../../stores/dashboard";
    import {usePluginsStore} from "../../../stores/plugins";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
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
        ON_TASK_EDITOR_CLICK_INJECTION_KEY,
        PARENT_PATH_INJECTION_KEY,
        POSITION_INJECTION_KEY,
        REF_PATH_INJECTION_KEY,
        ROOT_SCHEMA_INJECTION_KEY,
        SCHEMA_DEFINITIONS_INJECTION_KEY,
        UPDATE_YAML_FUNCTION_INJECTION_KEY
    } from "../../no-code/injectionKeys";
    import {NoCodeProps} from "../../flows/noCodeTypes";
    import {deepEqual} from "../../../utils/utils";

    const props = defineProps<NoCodeProps>();

    const {fieldsFromSchema, parsedSource} = useDashboardFields();

    const dashboardStore = useDashboardStore();

    function shouldMerge(schema: any): boolean {
        const complexObject = ["object", "array"].includes(schema?.type) || schema?.$ref || schema?.oneOf || schema?.anyOf || schema?.allOf;
        return !complexObject
    }

    function onTaskUpdateField(key: string, val: any) {
        const app = {
            ...parsedSource.value,
            [key]: val,
        };

        dashboardStore.sourceCode = YAML_UTILS.stringify(app);
    }

    provide(UPDATE_YAML_FUNCTION_INJECTION_KEY, (yaml) => {
        editorUpdate(yaml)
    })

    function editorUpdate(source: string) {
        // if no-code would not change the structure of the app,
        // do not trigger an update as it would remove all formatting and comments
        if(deepEqual(YAML_UTILS.parse(source), dashboardStore.sourceCode)) {
            return;
        }
        dashboardStore.sourceCode = source;
    }

    const emit = defineEmits<{
        (e: "createTask", parentPath: string, blockSchemaPath: string, refPath: number | undefined,  position: "after" | "before"): boolean | void;
        (e: "editTask", parentPath: string, blockSchemaPath: string, refPath?: number): boolean | void;
        (e: "closeTask"): boolean | void;
    }>();

    provide(CLOSE_TASK_FUNCTION_INJECTION_KEY, () => {
        emit("closeTask")
    })

    provide(CREATE_TASK_FUNCTION_INJECTION_KEY, (parentPath, blockSchemaPath, refPath) => {
        emit("createTask", parentPath, blockSchemaPath, refPath, "after")
    });

    provide(EDIT_TASK_FUNCTION_INJECTION_KEY, (...args) => {
        emit("editTask", ...args)
    });

    provide(FULL_SCHEMA_INJECTION_KEY, computed(() => dashboardStore.schema ?? {}));
    provide(ROOT_SCHEMA_INJECTION_KEY, computed(() => dashboardStore.rootSchema ?? {}));
    provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => dashboardStore.definitions ?? {}));

    provide(REF_PATH_INJECTION_KEY, props.refPath);
    provide(CREATING_TASK_INJECTION_KEY, props.creatingTask);
    provide(EDITING_TASK_INJECTION_KEY, props.editingTask);
    provide(FIELDNAME_INJECTION_KEY, props.fieldName);

    provide(PARENT_PATH_INJECTION_KEY, props.parentPath ?? "");
    provide(BLOCK_SCHEMA_PATH_INJECTION_KEY, computed(() => props.blockSchemaPath ?? dashboardStore.schema.$ref ?? ""));
    provide(FULL_SOURCE_INJECTION_KEY, computed(() => dashboardStore.sourceCode ?? ""));
    provide(POSITION_INJECTION_KEY, props.position ?? "after");
    provide(ON_TASK_EDITOR_CLICK_INJECTION_KEY, (elt) => {
        const type = elt?.type;
        dashboardStore.loadChart(elt);
        if(type){
            pluginsStore.updateDocumentation({type});
        }else{
            pluginsStore.updateDocumentation(); 
        }
    })

    const pluginsStore = usePluginsStore();

    onActivated(() => {
        pluginsStore.updateDocumentation();
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
