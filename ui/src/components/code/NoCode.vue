<template>
    <div class="no-code">
        <Editor
            @update-task="(yaml) => emit('updateTask', yaml)"
            @reorder="(yaml) => emit('reorder', yaml)"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, provide, ref} from "vue";

    import {
        CLOSE_TASK_FUNCTION_INJECTION_KEY,
        CREATING_TASK_INJECTION_KEY,
        PANEL_INJECTION_KEY, POSITION_INJECTION_KEY,
        REF_PATH_INJECTION_KEY, PARENT_PATH_INJECTION_KEY,
        FLOW_INJECTION_KEY, FIELDNAME_INJECTION_KEY,
        EDITING_TASK_INJECTION_KEY, BLOCK_SCHEMA_PATH_INJECTION_KEY
    } from "./injectionKeys";
    import Editor from "./segments/Editor.vue";

    const emit = defineEmits<{
        (e: "updateTask", yaml: string): void
        (e: "reorder", yaml: string): void
        (e: "createTask", parentPath: string, refPath: number | undefined, position?: "before" | "after"): boolean | void
        (e: "editTask", parentPath: string, refPath?: number): boolean | void
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
             * Initial block index when opening
             * a no-code panel from topology
             */
            refPath?: number;
            creatingTask?: boolean;
            editingTask?: boolean;
            position?: "before" | "after";
            blockSchemaPath?: string;
            fieldName?: string | undefined;
        }>(), {
            creatingTask: false,
            editingTask: false,
            position: "after",
            refPath: undefined,
            parentPath: undefined,
            blockSchemaPath: "",
            fieldName: undefined,
        });


    const panel = ref()

    provide(FLOW_INJECTION_KEY, computed(() => props.flow));
    provide(PARENT_PATH_INJECTION_KEY, props.parentPath ?? "");
    provide(REF_PATH_INJECTION_KEY, props.refPath);
    provide(PANEL_INJECTION_KEY, panel)
    provide(POSITION_INJECTION_KEY, props.position);
    provide(CREATING_TASK_INJECTION_KEY, props.creatingTask);
    provide(EDITING_TASK_INJECTION_KEY, props.editingTask);
    provide(BLOCK_SCHEMA_PATH_INJECTION_KEY, props.blockSchemaPath);
    provide(FIELDNAME_INJECTION_KEY, props.fieldName);

    provide(CLOSE_TASK_FUNCTION_INJECTION_KEY, () => {
        emit("closeTask")
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
