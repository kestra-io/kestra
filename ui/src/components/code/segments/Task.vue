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
        @update:model-value="validateTask"
    />

    <template v-if="yaml">
        <ValidationError v-if="false" :errors link />

        <Save
            :disabled="(errors?.length ?? 0) > 0"
            @click="exitTaskElement"
            :what="section"
            class="w-100 mt-3"
        />
    </template>
</template>

<script setup lang="ts">
    import {onBeforeMount, ref, watch, computed, inject} from "vue";
    import {useStore} from "vuex";
    import {SECTIONS} from "@kestra-io/ui-libs";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import {PLUGIN_DEFAULTS_SECTION, SECTIONS_MAP} from "../../../utils/constants";
    import {
        BREADCRUMB_INJECTION_KEY, CLOSE_TASK_FUNCTION_INJECTION_KEY,
        FLOW_INJECTION_KEY, FLOW_BEFORE_ADD_INJECTION_KEY,
        PARENT_PATH_INJECTION_KEY, POSITION_INJECTION_KEY,
        TASK_CREATION_INDEX_INJECTION_KEY, REF_PATH_INJECTION_KEY,
    } from "../injectionKeys";
    import TaskEditor from "../../../components/flows/TaskEditor.vue";
    import ValidationError from "../../../components/flows/ValidationError.vue";
    import Save from "../components/Save.vue";
    import {BlockType} from "../utils/types";

    const emits = defineEmits(["updateTask", "exitTask", "updateDocumentation"]);

    const flow = inject(FLOW_INJECTION_KEY, ref(""));
    const flowBeforeAdd = inject(FLOW_BEFORE_ADD_INJECTION_KEY, ref(""));
    const parentPath = inject(PARENT_PATH_INJECTION_KEY, "");
    const refPath = inject(REF_PATH_INJECTION_KEY, undefined);
    const position = inject(POSITION_INJECTION_KEY, "after");
    const taskCreationIndex = inject(
        TASK_CREATION_INDEX_INJECTION_KEY,
        ref(0),
    );
    const exitTaskElement = inject(
        CLOSE_TASK_FUNCTION_INJECTION_KEY,
        () => {},
    );

    const store = useStore();

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
        blockType?: BlockType
    }

    const yaml = taskCreationIndex.value ? computed({
        get() {
            return store.getters["flow/createdTasks"]?.[taskCreationIndex.value - 1]?.newBlock ?? "";
        },
        set(val){
            store.commit("flow/setCreatedTask", {
                index: taskCreationIndex.value - 1,
                newBlock: val,
                parentPath,
                refPath,
                position,
            } satisfies (TaskModel & {
                index: number,
            }));
        }
    }) : ref("");

    onBeforeMount(() => {
        if(!taskCreationIndex.value){
            yaml.value = YAML_UTILS.extractBlockWithPath({
                source: flow.value,
                path: parentPath + refPath,
            })
            const type = YAML_UTILS.parse(yaml.value)?.type ?? null;
            emits("updateDocumentation", type);
        }
    });

    const section = computed(() => /^(\w+)(\[\d+\])?/.exec(parentPath)?.[1]);

    const validationSection = computed(() =>
        section.value === "triggers" ? SECTIONS.TRIGGERS : SECTIONS.TASKS
    )

    const parsedTask = computed(() => YAML_UTILS.parse(yaml.value));

    const validateTask = (task?: string) => {
        if(section.value !== PLUGIN_DEFAULTS_SECTION){
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
        let result: string = "";

        const currentSection = section.value as keyof typeof SECTIONS_MAP;

        if(!currentSection) {
            return;
        }

        const keyName = currentSection === PLUGIN_DEFAULTS_SECTION ? "type" : "id"

        if (taskCreationIndex.value) {
            // if multiple task creation tabs are open add them all
            const tasks: TaskModel[] | undefined = store.getters["flow/createdTasks"];
            result = flowBeforeAdd.value;
            if(!tasks || !tasks.length) {
                return;
            }

            for(const task of tasks){
                if(!task?.newBlock){
                    continue;
                }
                if([PLUGIN_DEFAULTS_SECTION, "tasks", "triggers"].includes(currentSection)){
                    const parsedTask = YAML_UTILS.parse(task.newBlock);
                    // this condition will ignore trigger "conditions" unicity
                    if(parsedTask?.[keyName]){
                        const existing = YAML_UTILS.checkBlockAlreadyExists({
                            source: flowBeforeAdd.value,
                            section: SECTIONS_MAP[currentSection],
                            newContent: task.newBlock,
                            keyName,
                        })

                        if (existing) {
                            store.dispatch("core/showMessage", {
                                variant: "error",
                                title: "Block with same ID already exist",
                                message: `Block in ${section.value} section with ID: ${existing} already exist in the flow.`,
                            });
                        }
                    }
                }

                result = YAML_UTILS.insertBlockWithPath({
                    source: result,
                    ...task,
                });
            }
        } else {
            result = YAML_UTILS.replaceBlockWithPath({
                source: flow.value,
                path: `${parentPath}[${refPath}]`,
                newContent: yaml.value ?? "",
            });
        }

        emits("updateTask", result);
    };

    watch(
        yaml,
        () => {
            saveTask()
        },
    );

    watch(flowBeforeAdd, () => {
        if (taskCreationIndex.value) {
            saveTask()
        }
    });
</script>
