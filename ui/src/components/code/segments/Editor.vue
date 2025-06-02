<template>
    <div class="p-4">
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

        <template v-else-if="!creatingTask && refPath === undefined">
            <el-form label-position="top">
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
                    v-for="(section, index) in sections"
                    :key="index"
                    v-bind="section"
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
            </el-form>
        </template>

        <Task
            v-else
            @update-task="onTaskUpdate"
        />
    </div>
</template>

<script setup lang="ts">
    import {onMounted, computed, inject, ref} from "vue";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";

    import {Field, Fields, CollapseItem, NoCodeElement, BlockType} from "../utils/types";

    import Collapse from "../components/collapse/Collapse.vue";
    import InputText from "../components/inputs/InputText.vue";
    import InputSwitch from "../components/inputs/InputSwitch.vue";
    import InputPair from "../components/inputs/InputPair.vue";

    import Editor from "../../inputs/Editor.vue";
    import MetadataInputs from "../../flows/MetadataInputs.vue";
    import MetadataRetry from "../../flows/MetadataRetry.vue";
    import MetadataSLA from "../../flows/MetadataSLA.vue";
    import TaskBasic from "../../flows/tasks/TaskBasic.vue";

    import {
        CREATING_TASK_INJECTION_KEY, FLOW_INJECTION_KEY,
        PANEL_INJECTION_KEY, REF_PATH_INJECTION_KEY,
    } from "../injectionKeys";

    import Task from "./Task.vue";

    const panel = inject(PANEL_INJECTION_KEY, ref());
    const refPath = inject(REF_PATH_INJECTION_KEY, undefined);


    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import {useStore} from "vuex";
    const store = useStore();

    const emits = defineEmits([
        "save",
        "updateTask",
        "updateMetadata",
        "reorder",
    ]);

    const saveEvent = (e: KeyboardEvent) => {
        if (e.type === "keydown" && e.key === "s" && e.ctrlKey) {
            e.preventDefault();
            emits("save");
        }
    };

    document.addEventListener("keydown", saveEvent);

    const creatingFlow = computed(() => {
        return store.state.flow.isCreating;
    });

    const creatingTask = inject(CREATING_TASK_INJECTION_KEY);
    const flow = inject(FLOW_INJECTION_KEY, ref(""));

    const props = defineProps({
        metadata: {type: Object, required: true},
    });

    const trimmed = (field: Field) => {
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        const {component, value, ...rest} = field;

        return rest;
    };

    function onTaskUpdate(yaml: string) {
        emits("updateTask", yaml)
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
                disabled: !creatingFlow.value,
            },
            namespace: {
                component: InputText,
                value: props.metadata.namespace,
                label: t("no_code.fields.main.namespace"),
                required: true,
                disabled: !creatingFlow.value,
            },
            description: {
                component: InputText,
                value: props.metadata.description,
                label: t("no_code.fields.main.description"),
            },
            retry: {
                component: MetadataRetry,
                value: props.metadata.retry,
                label: t("no_code.fields.general.retry")
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
            sla: {
                component: MetadataSLA,
                value: props.metadata.sla ?? [],
                label: t("no_code.fields.general.sla")
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

    const SECTIONS_IDS = [
        "tasks",
        "triggers",
        "errors",
        "finally",
        "afterExecution",
        "pluginDefaults",
    ] as const


    const SECTION_BLOCK_MAP: Record<typeof SECTIONS_IDS[number], BlockType | "pluginDefaults"> = {
        tasks: "tasks",
        triggers: "triggers",
        errors: "tasks",
        finally: "tasks",
        afterExecution: "tasks",
        pluginDefaults: "pluginDefaults",
    } as const;

    type SectionKey = typeof SECTIONS_IDS[number];

    const sections = computed((): CollapseItem[] => {
        const parsedFlow = YAML_UTILS.parse<Partial<Record<SectionKey, NoCodeElement[]>>>(flow.value);
        return SECTIONS_IDS.map((section) => ({
            elements: parsedFlow?.[section] ?? [],
            title: t(`no_code.sections.${section}`),
            blockType: SECTION_BLOCK_MAP[section],
            section,
        }))
    });
</script>
