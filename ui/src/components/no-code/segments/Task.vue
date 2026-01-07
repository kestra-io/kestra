<template>
    <TaskEditor
        v-model="yaml"
        @update:model-value="validateTask(); saveTask();"
    />

    <template v-if="yaml">
        <ValidationError v-if="false" :errors link />
    </template>
</template>

<script setup lang="ts">
    import {ref, watch, computed, inject, nextTick} from "vue";
    import {SECTIONS} from "@kestra-io/ui-libs";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import {PLUGIN_DEFAULTS_SECTION, SECTIONS_MAP} from "../../../utils/constants";
    import {
        CLOSE_TASK_FUNCTION_INJECTION_KEY,
        UPDATE_YAML_FUNCTION_INJECTION_KEY,
        FULL_SOURCE_INJECTION_KEY, CREATING_TASK_INJECTION_KEY,
        PARENT_PATH_INJECTION_KEY, POSITION_INJECTION_KEY,
        REF_PATH_INJECTION_KEY, EDIT_TASK_FUNCTION_INJECTION_KEY,
        FIELDNAME_INJECTION_KEY, BLOCK_SCHEMA_PATH_INJECTION_KEY,
    } from "../injectionKeys";
    import TaskEditor from "../components/TaskEditor.vue";
    import ValidationError from "../../../components/flows/ValidationError.vue";
    import {useFlowStore} from "../../../stores/flow";

    const flow = inject(FULL_SOURCE_INJECTION_KEY, ref(""));
    const parentPath = inject(PARENT_PATH_INJECTION_KEY, "");
    const refPath = inject(REF_PATH_INJECTION_KEY, undefined);
    const position = inject(POSITION_INJECTION_KEY, "after");
    const creatingTask = inject(
        CREATING_TASK_INJECTION_KEY,
        false,
    );

    const fieldName = inject(FIELDNAME_INJECTION_KEY, undefined);
    const blockSchemaPath = inject(BLOCK_SCHEMA_PATH_INJECTION_KEY, ref(""));
    const updateTask = inject(UPDATE_YAML_FUNCTION_INJECTION_KEY, () => {})

    const closeTaskAddition = inject(
        CLOSE_TASK_FUNCTION_INJECTION_KEY,
        () => {},
    );
    const editTask = inject(
        EDIT_TASK_FUNCTION_INJECTION_KEY,
        () => {},
    );

    interface TaskModel {
        newBlock: string,
        parentPath: string,
        refPath?: number
        position?: "before" | "after",
    }

    const yaml = ref("");

    function getPath(parentPath: string, refPath: number | undefined): string {
        return refPath !== undefined && refPath !== null ? `${parentPath}[${refPath}]` : parentPath;
    }

    watch(flow, (source) => {
        if(!creatingTask){
            const path = getPath(parentPath, refPath);
            const taskYaml = YAML_UTILS.extractBlockWithPath({
                source,
                path,
            }) ?? ""

            if(taskYaml === yaml.value){
                return;
            }
            yaml.value = taskYaml;
        }
    }, {
        immediate: true,
    });

    const section = computed(() => /^(\w+)(\[\d+\])?/.exec(parentPath)?.[1]);

    const validationSection = computed(() =>
        section.value === "triggers" ? SECTIONS.TRIGGERS : SECTIONS.TASKS
    )

    const flowStore = useFlowStore();
    const validateTask = (task?: string) => {
        if(section.value !== PLUGIN_DEFAULTS_SECTION && task){
            clearTimeout(timer.value);
            timer.value = setTimeout(() => {
                if (lastValidatedValue.value !== task) {
                    lastValidatedValue.value = task;
                    flowStore.validateTask({
                        task,
                        section: validationSection.value
                    });
                }
            }, 500) as any;
        }
    };

    const timer = ref<number>();
    const lastValidatedValue = ref<string>();


    const errors = computed(() => flowStore.taskError?.split(/, ?/));

    const saveTask = () => {
        let result: string = flow.value;

        if (!creatingTask) {
            if(yaml.value){
                const path = getPath(parentPath, refPath);
                result = YAML_UTILS.replaceBlockWithPath({
                    source: result,
                    path,
                    newContent: yaml.value,
                });
            }
        } else if(!hasMovedToEdit.value ){
            const currentSection = section.value as keyof typeof SECTIONS_MAP;

            if(!currentSection) {
                return;
            }

            const task = {
                newBlock: yaml.value,
                parentPath,
                refPath,
                position,
            } satisfies TaskModel;

            result = YAML_UTILS.insertBlockWithPath({
                source: result,
                ...task,
            });


            const currentRefPath = (refPath !== undefined && refPath !== null) ? refPath + (position === "after" ? 1 : 0) : 0;
            editTask(
                fieldName ? `${parentPath}[${currentRefPath}].${fieldName}` : parentPath,
                blockSchemaPath.value,
                fieldName ? undefined : currentRefPath
            );
            hasMovedToEdit.value = true;
            nextTick(() => {
                closeTaskAddition();
            });
        }

        updateTask(result);
    };

    const hasMovedToEdit = ref(false);
</script>
