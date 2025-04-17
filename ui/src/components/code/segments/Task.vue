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
    import {CREATING_INJECTION_KEY, FLOW_INJECTION_KEY, SAVEMODE_INJECTION_KEY} from "../injectionKeys";
    import TaskEditor from "../../../components/flows/TaskEditor.vue";
    import ValidationError from "../../../components/flows/ValidationError.vue";
    import Save from "../components/Save.vue";

    const emits = defineEmits(["updateTask", "exitTask", "updateDocumentation"]);
    const props = withDefaults(defineProps<{
        section: "tasks" | "triggers" | "error handlers" | "finally" | "after execution";
        identifier: string;
        position?: "before" | "after";
    }>(), {
        position: "after"
    });

    const flow = inject(FLOW_INJECTION_KEY, "");
    const creation = inject(CREATING_INJECTION_KEY);
    const saveMode = inject(SAVEMODE_INJECTION_KEY, "button");

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
        YAML_UTILS.extractTask(flow, props.identifier)?.toString() || "",
    );

    onBeforeMount(() => {
        const type = YAML_UTILS.parse(yaml.value)?.type ?? null;
        emits("updateDocumentation", type);
    });

    const validationSection = computed(() =>
        SECTIONS[props.section === "triggers" ? "TRIGGERS" : "TASKS"]
    )

    watch(
        () => props.identifier,
        (value) => {
            if (value === "new") {
                yaml.value = "";
            } else {
                yaml.value =
                    YAML_UTILS.extractTask(flow, value)?.toString() || "";
            }
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
        }, 500);
    };

    const timer = ref(null);
    const lastValidatedValue = ref(null);

    const errors = computed(() => store.getters["flow/taskError"]);

    function exitTaskElement(){
        if (lastBreadcrumb.value.shown){
            store.commit("code/removeBreadcrumb", {last: true});
        } else {
            emits("exitTask");
        }
    }


    const saveTask = () => {
        if (lastBreadcrumb.value.shown && saveMode === "button") {
            exitTaskElement();
            return;
        }

        const task = YAML_UTILS.extractTask(
            yaml.value,
            YAML_UTILS.parse(yaml.value).id,
        );

        const isCreation =
            creation && (!props.identifier || props.identifier === "new");

        let result;

        if (isCreation) {
            if (props.section === "tasks" && CURRENT.value) {
                const existing = YAML_UTILS.checkTaskAlreadyExist(
                    flow,
                    CURRENT.value,
                );

                if (existing) {
                    store.dispatch("core/showMessage", {
                        variant: "error",
                        title: "Task with same ID already exist",
                        message: `Task in ${props.section} block  with ID: ${existing} already exist in the flow.`,
                    });
                    return;
                }

                const taskId = props.identifier


                result = YAML_UTILS.insertTask(
                    flow,
                    taskId,
                    task!,
                    props.position,
                );
            } else if (props.section === "triggers") {
                result = YAML_UTILS.insertSection("triggers", flow, CURRENT.value);
            } else if (props.section === "error handlers") {
                result = YAML_UTILS.insertSection("errors", flow, CURRENT.value);
            } else if (props.section === "finally") {
                result = YAML_UTILS.insertSection("finally", flow, CURRENT.value);
            } else if (props.section === "after execution") {
                result = YAML_UTILS.insertSection("afterExecution", flow, CURRENT.value);
            }
        } else {
            result = YAML_UTILS.replaceTaskInDocument(
                flow,
                props.identifier,
                task!,
            );
        }

        emits("updateTask", result);
        if(saveMode === "button") {
            store.commit("code/removeBreadcrumb", {last: true});
            emits("exitTask");
        }
    };
</script>
