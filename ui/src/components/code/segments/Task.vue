<template>
    <TaskEditor
        v-if="!lastBreadcrumb.shown"
        v-model="yaml"
        :section
        @update:model-value="validateTask"
    />

    <component
        v-else
        :is="lastBreadcrumb.component.type"
        v-bind="lastBreadcrumb.component.props"
        :model-value="lastBreadcrumb.component.props.modelValue"
        @update:model-value="validateTask"
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
    import {SECTIONS} from "../../../utils/constants";
    import {CREATING_INJECTION_KEY, FLOW_INJECTION_KEY, POSITION_INJECTION_KEY, SAVEMODE_INJECTION_KEY, SECTION_INJECTION_KEY, TASKID_INJECTION_KEY} from "../injectionKeys";
    import TaskEditor from "../../../components/flows/TaskEditor.vue";
    import ValidationError from "../../../components/flows/ValidationError.vue";
    import Save from "../components/Save.vue";

    const emits = defineEmits(["updateTask", "exitTask", "updateDocumentation"]);

    const flow = inject(FLOW_INJECTION_KEY, ref(""));
    const creation = inject(CREATING_INJECTION_KEY, ref(false));
    const saveMode = inject(SAVEMODE_INJECTION_KEY, "button");
    const section = inject(SECTION_INJECTION_KEY, ref("tasks"));
    const taskId = inject(TASKID_INJECTION_KEY, ref(""));
    const position = inject(POSITION_INJECTION_KEY, "after");

    const store = useStore();

    const breadcrumbs = computed(() => store.state.code.breadcrumbs);
    const lastBreadcrumb = computed(() => {
        const index =
            breadcrumbs.value.length === 3 ? 2 : breadcrumbs.value.length - 1;

        return {
            shown: index >= 2,
            component: breadcrumbs.value?.[index]?.component,
        };
    });

    const yaml = ref(
        YAML_UTILS.extractTask(flow.value, taskId.value)?.toString() || "",
    );

    const flowBeforeAdd = ref(flow.value);

    onBeforeMount(() => {
        const type = YAML_UTILS.parse(yaml.value)?.type ?? null;
        emits("updateDocumentation", type);
    });

    const validationSection = computed(() =>
        SECTIONS[section.value === "triggers" ? "TRIGGERS" : "TASKS"]
    )

    watch(
        taskId,
        (value) => {
            yaml.value =
                YAML_UTILS.extractTask(flow.value, value)?.toString() || "";
        },
        {immediate: true},
    );

    watch(
        yaml,
        (value) => {
            if(saveMode === "auto") {
                store.dispatch("flow/validateTask", {task: value, section: validationSection.value});
                saveTask();
            }
        },
    );

    const CURRENT = ref<string|null>(null);
    const validateTask = (task: string) => {
        let temp = YAML_UTILS.parse(yaml.value);

        if (lastBreadcrumb.value.shown) {
            const field = breadcrumbs.value.at(-1).label;
            temp = {...temp, [field]: task};
        }

        temp = YAML_UTILS.stringify(temp);

        store
            .dispatch("flow/validateTask", {task: temp, section: validationSection.value})
            .then(() => (yaml.value = temp));

        CURRENT.value = temp;

        clearTimeout(timer.value);
        timer.value = setTimeout(() => {
            if (lastValidatedValue.value !== temp) {
                lastValidatedValue.value = temp;
                store.dispatch("flow/validateTask", {task: temp, section: validationSection.value});
            }
        }, 500) as any;
    };

    const timer = ref<number>();
    const lastValidatedValue = ref(null);

    const errors = computed(() => store.getters["flow/taskError"]);

    const SECTIONS_MAP: Record<string, string> = {
        tasks: "task",
        triggers: "triggers",
        "error handlers": "errors",
        finally: "finally",
        "after execution": "afterExecution",
    };

    function exitTaskElement(){
        if (lastBreadcrumb.value.shown){
            store.commit("code/removeBreadcrumb", {last: true});
        } else {
            emits("exitTask");
            creation.value = false;
        }
    }


    const saveTask = () => {
        if (lastBreadcrumb.value.shown && saveMode === "button") {
            exitTaskElement();
            return;
        }

        const taskObject = YAML_UTILS.parse(yaml.value);
        taskObject.id = taskObject.id?.length ? taskObject.id : undefined;

        const task = YAML_UTILS.stringify(taskObject);

        let result: string = "";

        const currentSection = section.value;

        if (creation.value) {
            if (currentSection === "tasks" && task) {
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
                    flowBeforeAdd.value,
                    taskId.value.length ? taskId.value : YAML_UTILS.getLastTask(flowBeforeAdd.value) ?? "", // target task id (the one before of after the task will be inserted)
                    task,
                    position,
                );
            } else if (currentSection && SECTIONS_MAP[currentSection.toString()]) {
                result = YAML_UTILS.insertSection(
                    SECTIONS_MAP[currentSection.toString()],
                    flowBeforeAdd.value,
                    task
                );
            }
        } else if(task){
            result = YAML_UTILS.replaceTaskInDocument(
                flow.value,
                taskId.value,
                task,
            );
        }

        emits("updateTask", result);
        if(saveMode === "button") {
            store.commit("code/removeBreadcrumb", {last: true});
            emits("exitTask");
        }
    };
</script>
