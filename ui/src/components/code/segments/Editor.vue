<template>
    <div class="p-4">
        <template v-if="!route.query.section && !route.query.identifier">
            <template v-if="panel">
                <component
                    :is="panel.type"
                    :model-value="panel.props.modelValue"
                    v-bind="panel.props"
                    @update:model-value="
                        (value) => emits('updateMetadata', 'inputs', value)
                    "
                />
            </template>

            <template v-else>
                <component
                    v-for="([k, v], index) in Object.entries(getFields())"
                    :key="index"
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
                    v-for="([k, v], index) in Object.entries(getFields(false))"
                    :key="index"
                    :is="v.component"
                    v-model="v.value"
                    v-bind="trimmed(v)"
                    @update:model-value="emits('updateMetadata', k, v.value)"
                />
            </template>
        </template>

        <Task
            v-else
            :flow
            :creation
            @update-task="(yaml) => emits('updateTask', yaml)"
            @update-documentation="(task) => emits('updateDocumentation', task)"
        />
    </div>
</template>

<script setup lang="ts">
    import {watch, ref, shallowRef, computed} from "vue";

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

    const fields = ref<Fields>({
        id: {
            component: shallowRef(InputText),
            value: props.metadata.id,
            label: t("no_code.fields.main.flow_id"),
            required: true,
            disabled: !props.creation,
        },
        namespace: {
            component: shallowRef(InputText),
            value: props.metadata.namespace,
            label: t("no_code.fields.main.namespace"),
            required: true,
            disabled: !props.creation,
        },
        description: {
            component: shallowRef(InputText),
            value: props.metadata.description,
            label: t("no_code.fields.main.description"),
        },
        retry: {
            component: shallowRef(Editor),
            value: props.metadata.retry,
            label: t("no_code.fields.general.retry"),
            navbar: false,
            input: true,
            lang: "yaml",
            shouldFocus: false,
            style: {height: "100px"},
        },
        labels: {
            component: shallowRef(InputPair),
            value: props.metadata.labels,
            label: t("no_code.fields.general.labels"),
            property: t("no_code.labels.label"),
        },
        inputs: {
            component: shallowRef(MetadataInputs),
            value: props.metadata.inputs,
            label: t("no_code.fields.general.inputs"),
            inputs: props.metadata.inputs ?? [],
        },
        outputs: {
            component: shallowRef(Editor),
            value: props.metadata.outputs,
            label: t("no_code.fields.general.outputs"),
            navbar: false,
            input: true,
            lang: "yaml",
            shouldFocus: false,
            style: {height: "100px"},
        },
        variables: {
            component: shallowRef(InputPair),
            value: props.metadata.variables,
            label: t("no_code.fields.general.variables"),
            property: t("no_code.labels.variable"),
        },
        concurrency: {
            component: shallowRef(TaskBasic),
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
            component: shallowRef(Editor),
            value: props.metadata.pluginDefaults,
            label: t("no_code.fields.general.plugin_defaults"),
            navbar: false,
            input: true,
            lang: "yaml",
            shouldFocus: false,
            style: {height: "100px"},
        },
        disabled: {
            component: shallowRef(InputSwitch),
            value: props.metadata.disabled,
            label: t("no_code.fields.general.disabled"),
        },
    });

    const getFields = (main = true) => {
        const {id, namespace, description, inputs, ...rest} = fields.value;

        if (main) return {id, namespace, description, inputs};
        else return rest;
    };

    import YamlUtils from "../../../utils/yamlUtils";
    const getSectionTitle = (label: string, elements = []) => {
        const title = t(`no_code.sections.${label}`);
        return {title, elements};
    };
    const sections = computed((): CollapseItem[] => {
        return [
            getSectionTitle("tasks", YamlUtils.parse(props.flow).tasks ?? []),
            getSectionTitle("triggers", YamlUtils.parse(props.flow).triggers ?? []),
            getSectionTitle(
                "error_handlers",
                YamlUtils.parse(props.flow).errors ?? [],
            ),
            getSectionTitle("finally", YamlUtils.parse(props.flow).finally ?? []),
        ];
    });
</script>
