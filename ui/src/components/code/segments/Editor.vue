<template>
    <div class="p-4">
        <template v-if="!route.query.section && !route.query.identifier">
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
                    creation
                    :flow
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
            :identifier="taskIdentifier"
            :flow
            :creation
            @update-task="(yaml) => emits('updateTask', yaml)"
            @update-documentation="(task) => emits('updateDocumentation', task)"
        />
    </div>
</template>

<script setup lang="ts">
    import {watch, computed} from "vue";

    import {Field, Fields, CollapseItem} from "../utils/types";

    import Collapse from "../components/collapse/Collapse.vue";
    import InputText from "../components/inputs/InputText.vue";
    import InputSwitch from "../components/inputs/InputSwitch.vue";
    import InputPair from "../components/inputs/InputPair.vue";

    import Editor from "../../inputs/Editor.vue";
    import MetadataInputs from "../../flows/MetadataInputs.vue";
    import TaskBasic from "../../flows/tasks/TaskBasic.vue";

    import Task from "./Task.vue";

    import {useRoute} from "vue-router";
    const route = useRoute();

    const taskIdentifier = computed(
        () => route.query.identifier?.toString() ?? "new",
    );

    watch(
        () => route.query,
        async (newQuery) => {
            if (!newQuery?.section && !newQuery?.identifier) {
                emits("updateDocumentation", null);
            }
        },
        {deep: true},
    );

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

    const props = defineProps({
        creation: {type: Boolean, default: false},
        flow: {type: String, required: true},
        metadata: {type: Object, required: true},
    });

    const trimmed = (field: Field) => {
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        const {component, value, ...rest} = field;

        return rest;
    };

    const fields = computed<Fields>(() => {
        return {
            id: {
                component: InputText,
                value: props.metadata.id,
                label: t("no_code.fields.main.flow_id"),
                required: true,
                disabled: !props.creation,
            },
            namespace: {
                component: InputText,
                value: props.metadata.namespace,
                label: t("no_code.fields.main.namespace"),
                required: true,
                disabled: !props.creation,
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
                // TODO: Pass schema for concurrency dynamically
                schema: {
                    type: "object",
                    properties: {
                        behavior: {
                            type: "string",
                            enum: ["QUEUE", "CANCEL", "FAIL"],
                            default: "QUEUE",
                            markdownDescription: "Default value is : `QUEUE`",
                        },
                        limit: {type: "integer", exclusiveMinimum: 0},
                    },
                    required: ["limit"],
                },
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

    import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";
    const getSectionTitle = (label: string, elements: Record<string, any>[] = []) => {
        const title = t(`no_code.sections.${label}`);
        return {title, elements};
    };
    const sections = computed((): CollapseItem[] => {
        const flow:{
            tasks: Record<string, any>[];
            triggers: Record<string, any>[];
            errors: Record<string, any>[];
            finally: Record<string, any>[];
            afterExecution: Record<string, any>[];
        } = YAML_UTILS.parse(props.flow) as any;
        return [
            getSectionTitle("tasks", flow?.tasks ?? []),
            getSectionTitle("triggers", flow?.triggers ?? []),
            getSectionTitle(
                "error_handlers",
                flow?.errors ?? [],
            ),
            getSectionTitle("finally", flow?.finally ?? []),
            getSectionTitle(
                "after_execution",
                flow?.afterExecution ?? [],
            ),
        ];
    });
</script>
