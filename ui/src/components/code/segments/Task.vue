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
    import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";
    import {PLUGIN_DEFAULTS_SECTION, SECTIONS} from "../../../utils/constants";
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
            return store.getters["flow/createdTaskYaml"][section.value]?.[taskCreationIndex.value - 1] ?? "";
        },
        set(val){
            store.commit("flow/setCreatedTaskYaml", {
                section: section.value,
                index: taskCreationIndex.value - 1,
                yaml: val,
            });
        }
    }) : ref("");

    const flowBeforeAdd = ref(flow.value);

    onBeforeMount(() => {
        const type = YAML_UTILS.parse(yaml.value)?.type ?? null;
        emits("updateDocumentation", type);
    });

    const validationSection = computed(() =>
        SECTIONS[section.value === "triggers" ? "TRIGGERS" : "TASKS"]
    )

    watch(
        [taskId, section],
        ([id, section]) => {
            if(taskCreationIndex.value){
                return;
            }
            yaml.value =
                section === PLUGIN_DEFAULTS_SECTION ?
                    YAML_UTILS.extractPluginDefault(
                        flow.value,
                        id // this is the task type for the plugin defaults
                    )
                    :
                    YAML_UTILS.extractTask(flow.value, id) ?? "";
        },
        {immediate: true},
    );

    watch(
        yaml,
        () => {
            if(saveMode === "auto") {
                if(errors.value?.length > 0){
                    return;
                }
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

    const SECTIONS_MAP: Record<SectionKey, string> = {
        tasks: "task",
        triggers: "triggers",
        "error handlers": "errors",
        finally: "finally",
        "after execution": "afterExecution",
        [PLUGIN_DEFAULTS_SECTION]: "pluginDefaults",
    };

    const saveTask = () => {
        if (lastBreadcrumb.value.shown && saveMode === "button") {
            exitTaskElement();
            return;
        }

        let result: string = "";

        const currentSection = section.value;

        if (taskCreationIndex.value) {
            // if multiple task creation tabs are open add them all
            const tasks: string[] | undefined = store.getters["flow/createdTaskYaml"][section.value];
            result = flowBeforeAdd.value;
            if(!tasks || !tasks.length) {
                return;
            }
            for(const task of tasks){
                if (currentSection === "tasks" && task?.length) {
                    const existing = YAML_UTILS.checkTaskAlreadyExist(
                        flowBeforeAdd.value,
                        task,
                    );

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

                    result = YAML_UTILS.insertTask(
                        result,
                        // target task id (the one before of after the task will be inserted)
                        taskId.value.length ? taskId.value : YAML_UTILS.getLastTask(flowBeforeAdd.value, parentTaskId.value) ?? "",
                        task,
                        position,
                        parentTaskId.value,
                    );
                } else if (currentSection && SECTIONS_MAP[currentSection] && task?.length) {
                    result = YAML_UTILS.insertSection(
                        SECTIONS_MAP[currentSection],
                        flowBeforeAdd.value,
                        task,
                    );
                }
            }
        } else if (currentSection === PLUGIN_DEFAULTS_SECTION) {
            result = YAML_UTILS.replacePluginDefaultsInDocument(
                flow.value,
                parsedTask.value.type,
                yaml.value,
            );
        } else {
            const originalTask = YAML_UTILS.extractTask(flow.value, taskId.value);
            if(!originalTask)return;

            result = YAML_UTILS.replaceTaskInDocument(
                flow.value,
                taskId.value,
                yaml.value,
            );
            const updatedTask = YAML_UTILS.parse(yaml.value);
            taskId.value = updatedTask.id;
        }

        emits("updateTask", result);
        if(saveMode === "button") {
            breadcrumbs.value.pop();
            emits("exitTask");
        }
    };
</script>
