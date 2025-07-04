<template>
    <div class="no-code">
        <Breadcrumbs />

        <hr class="m-0">

        <Editor
            :metadata
            @update-metadata="(k, v) => emit('updateMetadata', {[k]: v})"
            @update-task="(yaml) => emit('updateTask', yaml)"
            @reorder="(yaml) => emit('reorder', yaml)"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, provide, ref} from "vue";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";

    import {
        BREADCRUMB_INJECTION_KEY, CLOSE_TASK_FUNCTION_INJECTION_KEY,
        CREATING_TASK_INJECTION_KEY, BLOCKTYPE_INJECT_KEY,
        PANEL_INJECTION_KEY, POSITION_INJECTION_KEY,
        REF_PATH_INJECTION_KEY, PARENT_PATH_INJECTION_KEY,
        FLOW_INJECTION_KEY, FIELDNAME_INJECTION_KEY,
        EDITING_TASK_INJECTION_KEY,
    } from "./injectionKeys";
    import Breadcrumbs from "./components/Breadcrumbs.vue";
    import Editor from "./segments/Editor.vue";
    import {Breadcrumb, BlockType} from "./utils/types";

    const emit = defineEmits<{
        (e: "updateTask", yaml: string): void
        (e: "updateMetadata", value: {[key: string]: any}): void
        (e: "reorder", yaml: string): void
        (e: "createTask", blockType: string, parentPath: string, refPath: number | undefined, position?: "before" | "after"): boolean | void
        (e: "editTask", blockType: string, parentPath: string, refPath?: number): boolean | void
        (e: "closeTask"): boolean | void
    }>()

    const props = withDefaults(
        defineProps<{
            flow: string;
            /**
             * The path of the parent block
             */
            parentPath?: string;
            /**
             * Type of block to create
             */
            blockType?: BlockType | "pluginDefaults";
            /**
             * Initial block index when opening
             * a no-code panel from topology
             */
            refPath?: number;
            creatingTask?: boolean;
            editingTask?: boolean;
            position?: "before" | "after";
            fieldName?: string | undefined;
        }>(), {
            creatingTask: false,
            editingTask: false,
            position: "after",
            refPath: undefined,
            blockType: undefined,
            parentPath: undefined,
            fieldName: undefined,
        });

    const metadata = computed(() => YAML_UTILS.getMetadata(props.flow));

    const breadcrumbs = ref<Breadcrumb[]>([])
    const panel = ref()

    provide(FLOW_INJECTION_KEY, computed(() => props.flow));
    provide(PARENT_PATH_INJECTION_KEY, props.parentPath ?? "");
    provide(REF_PATH_INJECTION_KEY, props.refPath);
    provide(PANEL_INJECTION_KEY, panel)
    provide(BREADCRUMB_INJECTION_KEY, breadcrumbs);
    provide(BLOCKTYPE_INJECT_KEY, props.blockType);
    provide(POSITION_INJECTION_KEY, props.position);
    provide(CREATING_TASK_INJECTION_KEY, props.creatingTask);
    provide(EDITING_TASK_INJECTION_KEY, props.editingTask);
    provide(FIELDNAME_INJECTION_KEY, props.fieldName);

    provide(CLOSE_TASK_FUNCTION_INJECTION_KEY, () => {
        if (breadcrumbs.value[breadcrumbs.value.length - 1].component) {
            breadcrumbs.value.pop();
        } else {
            // only close the tab if saving a task not a value
            emit("closeTask")
        }

    })
</script>

<style lang="scss" scoped>
    .no-code {
        height: 100%;
        overflow-y: auto;

        hr {
            margin: 0;
        }
    }
</style>
