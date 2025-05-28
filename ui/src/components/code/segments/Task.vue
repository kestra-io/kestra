<template>
    <component
        v-if="lastBreadcrumb"
        :is="lastBreadcrumb.type"
        v-bind="lastBreadcrumb.props"
        :model-value="parsedTask[field]"
        @update:model-value="validateTaskElement"
    />

    <TaskEditor
        v-else
        v-model="yaml"
        @update:model-value="validateTask(); saveTask();"
    />

    <template v-if="yaml">
        <ValidationError v-if="false" :errors link />

        <Save
            v-if="!lastBreadcrumb"
            :disabled="(errors?.length ?? 0) > 0"
            @click="exitTaskElement"
            :what="section"
            class="w-100 mt-3"
        />
    </template>
</template>

<script setup lang="ts">
    import {ref, watch, computed, inject, nextTick} from "vue";
    import {useStore} from "vuex";
    import {SECTIONS} from "@kestra-io/ui-libs";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import {PLUGIN_DEFAULTS_SECTION, SECTIONS_MAP} from "../../../utils/constants";
    import {
        BREADCRUMB_INJECTION_KEY, CLOSE_TASK_FUNCTION_INJECTION_KEY,
        FLOW_INJECTION_KEY, CREATING_TASK_INJECTION_KEY,
        PARENT_PATH_INJECTION_KEY, POSITION_INJECTION_KEY,
        REF_PATH_INJECTION_KEY,
        EDIT_TASK_FUNCTION_INJECTION_KEY, BLOCKTYPE_INJECT_KEY,
    } from "../injectionKeys";
    import TaskEditor from "../../../components/flows/TaskEditor.vue";
    import ValidationError from "../../../components/flows/ValidationError.vue";
    import Save from "../components/Save.vue";
    import {BlockType} from "../utils/types";

    const emits = defineEmits(["updateTask", "exitTask", "updateDocumentation"]);

    const store = useStore();

    const flow = inject(FLOW_INJECTION_KEY, ref(""));
    const parentPath = inject(PARENT_PATH_INJECTION_KEY, "");
    const refPath = inject(REF_PATH_INJECTION_KEY, undefined);
    const blockType = inject(BLOCKTYPE_INJECT_KEY, undefined);
    const position = inject(POSITION_INJECTION_KEY, "after");
    const creatingTask = inject(
        CREATING_TASK_INJECTION_KEY,
        ref(false),
    );
    const exitTaskElement = inject(
        CLOSE_TASK_FUNCTION_INJECTION_KEY,
        () => {},
    );

    const closeTask = inject(
        CLOSE_TASK_FUNCTION_INJECTION_KEY,
        () => {},
    );
    const editTask = inject(
        EDIT_TASK_FUNCTION_INJECTION_KEY,
        () => {},
    );

    const breadcrumbs = inject(
        BREADCRUMB_INJECTION_KEY,
        ref([])
    );

    const lastBreadcrumb = computed(() => {
        return breadcrumbs.value?.[breadcrumbs.value.length - 1]?.component
    });

    interface TaskModel {
        newBlock: string,
        parentPath: string,
        refPath?: number
        position?: "before" | "after",
        blockType?: BlockType | "pluginDefaults"
    }

    const yaml = ref("");

    watch(flow, (source) => {
        if(!creatingTask.value){
            const taskYaml = YAML_UTILS.extractBlockWithPath({
                source,
                path: `${parentPath}[${refPath}]`,
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

    const parsedTask = computed(() => YAML_UTILS.parse(yaml.value));

    const validateTask = (task?: string) => {
        if(section.value !== PLUGIN_DEFAULTS_SECTION && task){
            clearTimeout(timer.value);
            timer.value = setTimeout(() => {
                if (lastValidatedValue.value !== task) {
                    lastValidatedValue.value = task;
                    store.dispatch("flow/validateTask", {
                        task,
                        section: validationSection.value
                    });
                }
            }, 500) as any;
        }
    };

    const field = computed(() => {
        const index = breadcrumbs.value.length - 1;
        return breadcrumbs.value[index]?.label;
    });

    const validateTaskElement = (taskElement?: Record<string, any>) => {
        let temp = parsedTask.value;

        if (lastBreadcrumb.value.shown) {
            if (field.value && Object.keys(taskElement ?? {}).length) {
                temp[field.value] = taskElement;
            }
        }

        const task = YAML_UTILS.stringify(temp);

        yaml.value = task;
    };


    const timer = ref<number>();
    const lastValidatedValue = ref<string>();

    const errors = computed(() => store.getters["flow/taskError"]);

    const saveTask = () => {
        let result: string = flow.value;

        if (!creatingTask.value) {
            if(yaml.value){
                result = YAML_UTILS.replaceBlockWithPath({
                    source: result,
                    path: `${parentPath}[${refPath}]`,
                    newContent: yaml.value,
                });
            }
        }else if(!hasMovedToEdit.value && blockType){
            const currentSection = section.value as keyof typeof SECTIONS_MAP;

            if(!currentSection) {
                return;
            }

            const task = {
                newBlock: yaml.value,
                parentPath,
                refPath,
                position,
                blockType,
            } satisfies TaskModel;

            result = YAML_UTILS.insertBlockWithPath({
                source: result,
                ...task,
            });


            const currentRefPath = (refPath !== undefined && refPath !== null) ? refPath + (position === "after" ? 1 : 0) : 0;
            editTask(
                blockType,
                parentPath,
                currentRefPath,
            );
            hasMovedToEdit.value = true;
            nextTick(() => {
                closeTask();
            });
        }

        emits("updateTask", result);
    };

    const hasMovedToEdit = ref(false);
</script>
