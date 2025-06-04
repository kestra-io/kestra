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
                <TaskObjectField
                    v-for="(v) in fieldsFromSchema.slice(0, 4)"
                    :key="v.root"
                    v-bind="trimmed(v)"
                    @update:model-value="emits('updateMetadata', v.root, $event)"
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

                <TaskObjectField
                    v-for="(v) in fieldsFromSchema.slice(4)"
                    :key="v.root"
                    v-bind="trimmed(v)"
                    @update:model-value="emits('updateMetadata', v.root, $event)"
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

    import {CollapseItem, NoCodeElement, BlockType} from "../utils/types";

    import Collapse from "../components/collapse/Collapse.vue";

    import TaskObjectField from "../../flows/tasks/TaskObjectField.vue";


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

    const trimmed = (field: any) => {
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        const {component, label, ...rest} = field;

        return rest;
    };

    function onTaskUpdate(yaml: string) {
        emits("updateTask", yaml)
    }

    const schema = ref<{
        definitions?: any,
        $ref?: string,
    }>({})

    onMounted(async () => {
        await store.dispatch("plugin/loadSchemaType").then((response) => {
            schema.value = response;
        })
    });

    const definitions = computed(() => {
        return schema.value?.definitions ?? {};
    });
    function removeRefPrefix(ref?: string): string {
        return ref?.replace(/^#\/definitions\//, "") ?? "";
    }

    const flowSchema = computed(() => {
        const ref = removeRefPrefix(schema.value?.$ref);
        return definitions.value?.[ref];
    });

    const flowSchemaProperties = computed(() => {
        return flowSchema.value?.properties ?? {};
    });

    const rawFields = computed(() => {
        return [
            {
                root: "id",
                label: t("no_code.fields.main.flow_id"),
                disabled: !creatingFlow.value,
            },
            {
                root: "namespace",
                label: t("no_code.fields.main.namespace"),
                disabled: !creatingFlow.value,
            },
            {
                root: "description",
                label: t("no_code.fields.main.description"),
            },
            {
                root: "inputs",
            },
            {
                root: "retry",
            },
            {
                root: "labels",
            },
            {
                root: "outputs",
            },
            {
                root: "variables",
            },
            {
                root: "concurrency",
            },
            {
                root: "sla",
            },
            {
                root: "disabled",
            }
        ]
    });

    const fieldsFromSchema = computed(() => {
        if( !flowSchema.value || !flowSchemaProperties.value) {
            return [];
        }
        return rawFields.value.map(f => ({
            modelValue: props.metadata[f.root],
            required: flowSchema.value?.required ?? [],
            disabled: f.disabled ?? false,
            schema: flowSchemaProperties.value[f.root],
            definitions: definitions.value,
            label: f.label ?? t(`no_code.fields.general.${f.root}`),
            fieldKey: f.root,
            task: props.metadata,
            ...f,
        }));
    });

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
