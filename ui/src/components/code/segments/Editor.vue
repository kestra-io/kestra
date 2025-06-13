<template>
    <div class="p-4">
        <template v-if="panel">
            <MetadataInputsContent
                :inputs="metadata.inputs"
                :label="t('inputs')"
                :selected-index="panel.props.selectedIndex"
                @update:inputs="
                    (value: any) => emits('updateMetadata', 'inputs', value)
                "
            />
        </template>

        <template v-else-if="!creatingTask && !editingTask">
            <el-form label-position="top" v-if="fieldsFromSchema.length">
                <TaskWrapper :key="v.root" v-for="(v) in fieldsFromSchema.slice(0, 3)" :merge="shouldMerge(v.schema)">
                    <template #tasks>
                        <TaskObjectField
                            v-bind="v"
                            @update:model-value="updateMetadata(v.root, $event)"
                        />
                    </template>
                </TaskWrapper>

                <MetadataInputs
                    v-if="flowSchemaProperties.inputs"
                    :label="t('no_code.fields.general.inputs')"
                    :model-value="metadata.inputs"
                    :required="flowSchema.required?.includes('inputs')"
                    @update:model-value="updateMetadata('inputs', $event)"
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

                <TaskWrapper :key="v.root" v-for="(v) in fieldsFromSchema.slice(4)" :merge="shouldMerge(v.schema)">
                    <template #tasks>
                        <TaskObjectField
                            v-bind="v"
                            @update:model-value="updateMetadata(v.root, $event)"
                        />
                    </template>
                </TaskWrapper>
            </el-form>
            <template v-else>
                <el-skeleton
                    animated
                    :rows="4"
                    :throttle="{leading: 500, initVal: true}"
                />
                <hr class="my-4">
                <el-skeleton
                    animated
                    :rows="6"
                    :throttle="{leading: 500, initVal: true}"
                />
                <hr class="my-4">
                <el-skeleton
                    animated
                    :rows="5"
                    :throttle="{leading: 500, initVal: true}"
                />
            </template>
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

    import MetadataInputs from "../../flows/MetadataInputs.vue";
    import MetadataInputsContent from "../../flows/MetadataInputsContent.vue";
    import TaskObjectField from "../../flows/tasks/TaskObjectField.vue";


    import {
        CREATING_TASK_INJECTION_KEY, EDITING_TASK_INJECTION_KEY,
        FLOW_INJECTION_KEY, PANEL_INJECTION_KEY,
    } from "../injectionKeys";

    import Task from "./Task.vue";

    const panel = inject(PANEL_INJECTION_KEY, ref());

    const editingTask = inject(EDITING_TASK_INJECTION_KEY, false);


    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import {useStore} from "vuex";
    import TaskWrapper from "../../flows/tasks/TaskWrapper.vue";
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

    function shouldMerge(schema: any): boolean {
        const complexObject = ["object", "array"].includes(schema?.type) || schema?.$ref || schema?.oneOf || schema?.anyOf || schema?.allOf;
        return !complexObject
    }

    function updateMetadata(key: string, val: any) {
        const realValue = val === null || val === undefined || (typeof val === "object" && Object.keys(val).length === 0) ? undefined : val; // Handle null values
        emits("updateMetadata", key, realValue);
    }

    document.addEventListener("keydown", saveEvent);

    const creatingFlow = computed(() => {
        return store.state.flow.isCreating;
    });

    const creatingTask = inject(CREATING_TASK_INJECTION_KEY);
    const flow = inject(FLOW_INJECTION_KEY, ref(""));

    const props = defineProps({
        metadata: {type: Object, required: true},
    });

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

    const METADATA_KEYS = [
        "id",
        "namespace",
        "description",
        "inputs",
        "retry",
        "labels",
        "outputs",
        "variables",
        "concurrency",
        "sla",
        "disabled"
    ] as const;


    const fieldsFromSchema = computed(() => {
        if( !flowSchema.value || !flowSchemaProperties.value) {
            return [];
        }

        // FIXME: some labels are not where you would expect them to be
        const mainLabels: Record<string, string> = {
            id: t("no_code.fields.main.flow_id"),
            namespace: t("no_code.fields.main.namespace"),
            description: t("no_code.fields.main.description"),
        }

        return METADATA_KEYS.map(f => ({
            modelValue: props.metadata[f],
            required: flowSchema.value?.required ?? [],
            disabled: !creatingFlow.value && (f === "id" || f === "namespace"),
            schema: flowSchemaProperties.value[f],
            definitions: definitions.value,
            label: mainLabels[f] ?? t(`no_code.fields.general.${f}`),
            fieldKey: f,
            task: props.metadata,
            root: f,
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
