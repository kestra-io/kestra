<template>
    <TaskEditor
        v-if="!lastBreadcrumb.shown"
        v-model="yaml"
        :section
        @update:model-value="validateTask"
    />

    <component
        v-else-if="lastBreadcrumb.component"
        :is="lastBreadcrumb.component.type"
        v-bind="lastBreadcrumb.component.props"
        :model-value="parsedTask[field]"
        @update:model-value="validateTaskElement"
    />

    <template v-if="yaml">
        <!-- TODO: Improve the validation for single tasks -->
        <ValidationError v-if="false" :errors link />

        <Save
            v-if="!lastBreadcrumb.component"
            :disabled="(errors?.length ?? 0) > 0"
            @click="() => {
                saveTask();
                exitTaskElement();
            }"
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
        FLOW_INJECTION_KEY, PARENT_TASKID_INJECTION_KEY, POSITION_INJECTION_KEY,
        SAVEMODE_INJECTION_KEY, SECTION_INJECTION_KEY,
        TASK_CREATION_INDEX_INJECTION_KEY, TASKID_INJECTION_KEY
    } from "../injectionKeys";
    import TaskEditor from "../../../components/flows/TaskEditor.vue";
    import ValidationError from "../../../components/flows/ValidationError.vue";
    import Save from "../components/Save.vue";
    import {SectionKey} from "../utils/types";

    const emits = defineEmits(["updateTask", "exitTask", "updateDocumentation"]);

    const flow = inject(FLOW_INJECTION_KEY, ref(""));
    const saveMode = inject(SAVEMODE_INJECTION_KEY, "button");
    const section = inject(SECTION_INJECTION_KEY, ref("tasks" as SectionKey));
    const taskId = inject(TASKID_INJECTION_KEY, ref(""));
    const position = inject(POSITION_INJECTION_KEY, "after");
    const parentTaskId = inject(PARENT_TASKID_INJECTION_KEY, ref());
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
        const index = breadcrumbs.value.length - 1;

        return {
            shown: parentTaskId.value ? index >= 3 : index >= 2,
            component: breadcrumbs.value?.[index]?.component,
        };
    });

    const yaml = taskCreationIndex.value ? computed({
        get() {
            if(!section.value){
                return "";
            }
            return store.getters["flow/createdTasks"][section.value]?.[taskCreationIndex.value - 1]?.yaml ?? "";
        },
        set(val){
            store.commit("flow/setCreatedTask", {
                section: section.value,
                index: taskCreationIndex.value - 1,
                yaml: val,
                position: position,
                parentKey: parentTaskId.value,
                refKey: taskId.value,
            });
        }
    }) : ref("");

    const flowBeforeAdd = ref(flow.value);

    onBeforeMount(() => {
        const type = YAML_UTILS.parse(yaml.value)?.type ?? null;
        emits("updateDocumentation", type);
    });

    const validationSection = computed(() =>
        section.value === "triggers" ? SECTIONS.TRIGGERS : SECTIONS.TASKS
    )

    watch(
        [taskId, section],
        ([id, section]) => {
            if(taskCreationIndex.value || !section){
                return;
            }
            yaml.value =
                section === PLUGIN_DEFAULTS_SECTION ?
                    YAML_UTILS.extractPluginDefault(
                        flow.value,
                        id // this is the task type for the plugin defaults
                    )
                    :
                    YAML_UTILS.extractBlock({
                        source: flow.value,
                        section,
                        key: id
                    }) ?? "";
        },
        {immediate: true},
    );

    watch(
        yaml,
        () => {
            if(saveMode === "auto") {
                saveTask();
            }
        },
    );

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
        if (lastBreadcrumb.value.shown && saveMode === "button") {
            exitTaskElement();
            return;
        }

        let result: string | undefined = "";

        const currentSection = section.value;

        if(!currentSection) {
            return;
        }

        const keyName = currentSection === PLUGIN_DEFAULTS_SECTION ? "type" : "id"

        if (taskCreationIndex.value) {
            // if multiple task creation tabs are open add them all
            const tasks: {
                yaml:string,
                position?: "before" | "after",
                parentKey?: string,
                refKey?: string
            }[] | undefined = store.getters["flow/createdTasks"][currentSection];
            result = flowBeforeAdd.value;
            if(!tasks || !tasks.length) {
                return;
            }

            for(const task of tasks){
                if(!task?.yaml){
                    continue;
                }
                const parsedTask = YAML_UTILS.parse(task.yaml);
                if(parsedTask?.[keyName]){
                    const existing = YAML_UTILS.checkBlockAlreadyExists({
                        source: flowBeforeAdd.value,
                        section: SECTIONS_MAP[currentSection],
                        newContent: task.yaml,
                        keyName,
                    })

                    if (existing) {
                        store.dispatch("core/showMessage", {
                            variant: "error",
                            title: "Task with same ID already exist",
                            message: `Task in ${section} block  with ID: ${existing} already exist in the flow.`,
                        });

                        if(saveMode === "button"){
                            return;
                        }
                    }
                }

                const refKey = taskId.value.length ? taskId.value : YAML_UTILS.getLastBlock({
                    source: flowBeforeAdd.value,
                    section: SECTIONS_MAP[currentSection],
                    parentKey: task.parentKey,
                })

                result = YAML_UTILS.insertBlock({
                    source: result ?? "",
                    section: SECTIONS_MAP[currentSection],
                    // target task id (the one before of after the task will be inserted)
                    refKey,
                    newBlock: task.yaml,
                    position,
                    parentKey: task.parentKey,
                });
            }
        } else if (currentSection === PLUGIN_DEFAULTS_SECTION) {
            result = YAML_UTILS.replaceBlockInDocument({
                source: flow.value,
                section: SECTIONS_MAP[currentSection],
                key: parsedTask.value.type,
                newContent: yaml.value,
                keyName: "type",
            });
        } else {
            const originalTask = YAML_UTILS.extractBlock({
                source:flow.value,
                section: currentSection,
                key:taskId.value
            });
            if(!originalTask)return;

            result = YAML_UTILS.replaceBlockInDocument({
                source: flow.value,
                section: SECTIONS_MAP[currentSection],
                key: taskId.value,
                newContent: yaml.value,
                keyName: "id",
            });
            const updatedTask = YAML_UTILS.parse(yaml.value);
            taskId.value = updatedTask.id;
        }

        emits("updateTask", result ?? "");
        if(saveMode === "button") {
            breadcrumbs.value.pop();
            emits("exitTask");
        }
    };
</script>
