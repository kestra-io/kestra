<template>
    <div class="p-4">
        <template v-if="!taskSection && !taskIdentifier">
            <template v-if="panel">
                <component
                    :is="panel.type"
                    :model-value="panel.props.modelValue"
                    v-bind="panel.props"
                    @update:model-value="
                        (value: any) => emits('updateMetadata', 'inputs', value)
                    "
                />
            </template>

            <template v-else>
                <component
                    v-for="(v, k) in mainFields"
                    :key="k"
                    :is="v.component"
                    v-model="v.value"
                    v-bind="trimmed(v)"
                    @update:model-value="emits('updateMetadata', k, v.value)"
                />

                <hr class="my-4">

                <Collapse
                    :items="sections"
                    @remove="(yaml) => emits('updateTask', yaml)"
                    @reorder="(yaml) => emits('reorder', yaml)"
                />

                <hr class="my-4">

                <component
                    v-for="(v, k) in otherFields"
                    :key="k"
                    :is="v.component"
                    v-model="v.value"
                    v-bind="trimmed(v)"
                    @update:model-value="emits('updateMetadata', k, v.value)"
                />
            </template>
        </template>

        <Task
            v-else
            :key="taskIdentifier"
            @exit-task="exitTask"
            @update-task="onTaskUpdate"
            @update-documentation="(task) => emits('updateDocumentation', task)"
        />
    </div>
</template>

<script setup lang="ts">
    import {onMounted, watch, computed, inject, ref} from "vue";
    import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";

    import {Field, Fields, CollapseItem, NoCodeElement} from "../utils/types";

    import Collapse from "../components/collapse/Collapse.vue";
    import InputText from "../components/inputs/InputText.vue";
    import InputSwitch from "../components/inputs/InputSwitch.vue";
    import InputPair from "../components/inputs/InputPair.vue";

    import Editor from "../../inputs/Editor.vue";
    import MetadataInputs from "../../flows/MetadataInputs.vue";
    import TaskBasic from "../../flows/tasks/TaskBasic.vue";

    import {CREATING_INJECTION_KEY, FLOW_INJECTION_KEY, SAVEMODE_INJECTION_KEY, SECTION_INJECTION_KEY, TASKID_INJECTION_KEY} from "../injectionKeys";

    import Task from "./Task.vue";


    const taskIdentifier = computed(
        () => taskId.value?.toString() ?? "new",
    );



    const sectionInjected = inject(SECTION_INJECTION_KEY, ref(""));
    const taskId = inject(TASKID_INJECTION_KEY, ref(""));

    watch(
        [sectionInjected, taskId],
        ([section, id]) => {
            if (section && id) {
                emits("updateDocumentation", null);
            }
        },
    );

    const taskSection = computed(() => {
        return (sectionInjected.value ?? "TASKS").toString() as "tasks" | "triggers"
    });

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import {useStore} from "vuex";
    const store = useStore();

    const panel = computed(() => store.state.code.panel);

    const emits = defineEmits([
        "save",
        "updateTask",
        "updateMetadata",
        "updateDocumentation",
        "reorder",
    ]);

    const saveEvent = (e: KeyboardEvent) => {
        if (e.type === "keydown" && e.key === "s" && e.ctrlKey) {
            e.preventDefault();
            emits("save");
        }
    };

    document.addEventListener("keydown", saveEvent);

    const creation = inject(CREATING_INJECTION_KEY);
    const flow = inject(FLOW_INJECTION_KEY, ref(""));
    const saveMode = inject(SAVEMODE_INJECTION_KEY, "button");

    const props = defineProps({
        metadata: {type: Object, required: true},
    });

    const trimmed = (field: Field) => {
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        const {component, value, ...rest} = field;

        return rest;
    };

    function exitTask() {
        sectionInjected.value = "";
        taskId.value = "";
    }

    function onTaskUpdate(yaml: string) {
        emits("updateTask", yaml)

        if(saveMode === "button") {
            exitTask()
        }
    }

    const schema = ref({})
    onMounted(async () => {
        await store.dispatch("plugin/loadSchemaType").then((response) => {
            schema.value = response;
        })
    });

    const fields = computed<Fields>(() => {
        return {
            id: {
                component: InputText,
                value: props.metadata.id,
                label: t("no_code.fields.main.flow_id"),
                required: true,
                disabled: !creation,
            },
            namespace: {
                component: InputText,
                value: props.metadata.namespace,
                label: t("no_code.fields.main.namespace"),
                required: true,
                disabled: !creation,
            },
            description: {
                component: InputText,
                value: props.metadata.description,
                label: t("no_code.fields.main.description"),
            },
            retry: {
                component: Editor,
                value: props.metadata.retry,
                label: t("no_code.fields.general.retry"),
                navbar: false,
                input: true,
                lang: "yaml",
                shouldFocus: false,
                showScroll: true,
                style: {height: "100px"},
            },
            labels: {
                component: InputPair,
                value: props.metadata.labels,
                label: t("no_code.fields.general.labels"),
                property: t("no_code.labels.label"),
            },
            inputs: {
                component: MetadataInputs,
                value: props.metadata.inputs,
                label: t("no_code.fields.general.inputs"),
                inputs: props.metadata.inputs ?? [],
            },
            outputs: {
                component: Editor,
                value: props.metadata.outputs,
                label: t("no_code.fields.general.outputs"),
                navbar: false,
                input: true,
                lang: "yaml",
                shouldFocus: false,
                showScroll: true,
                style: {height: "100px"},
            },
            variables: {
                component: InputPair,
                value: props.metadata.variables,
                label: t("no_code.fields.general.variables"),
                property: t("no_code.labels.variable"),
            },
            concurrency: {
                component: TaskBasic,
                value: props.metadata.concurrency,
                label: t("no_code.fields.general.concurrency"),
                schema: schema.value?.definitions?.["io.kestra.core.models.flows.Concurrency"] ?? {},               
                root: "concurrency",
            },
            pluginDefaults: {
                component: Editor,
                value: props.metadata.pluginDefaults,
                label: t("no_code.fields.general.plugin_defaults"),
                navbar: false,
                input: true,
                lang: "yaml",
                shouldFocus: false,
                showScroll: true,
                style: {height: "100px"},
            },
            disabled: {
                component: InputSwitch,
                value: props.metadata.disabled,
                label: t("no_code.fields.general.disabled"),
            },
        }
    });

    const mainFields = computed(() => {
        const {id, namespace, description, inputs} = fields.value;

        return {id, namespace, description, inputs};
    })

    const otherFields = computed(() => {
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        const {id, namespace, description, inputs, ...rest} = fields.value;

        return rest;
    })


    const getSectionTitle = (label: string, elements: NoCodeElement[] = []) => {
        const title = t(`no_code.sections.${label}`);
        return {title, elements};
    };
    const sections = computed((): CollapseItem[] => {
        const parsedFlow = YAML_UTILS.parse<{
            tasks: NoCodeElement[];
            triggers: NoCodeElement[];
            errors: NoCodeElement[];
            finally: NoCodeElement[];
            afterExecution: NoCodeElement[];
        }>(flow.value);
        return [
            getSectionTitle("tasks", parsedFlow?.tasks ?? []),
            getSectionTitle("triggers", parsedFlow?.triggers ?? []),
            getSectionTitle(
                "error_handlers",
                parsedFlow?.errors ?? [],
            ),
            getSectionTitle("finally", parsedFlow?.finally ?? []),
            getSectionTitle(
                "after_execution",
                parsedFlow?.afterExecution ?? [],
            ),
        ];
    });
</script>
