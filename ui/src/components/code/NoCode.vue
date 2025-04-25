<template>
    <div class="h-100 overflow-y-auto no-code">
        <Breadcrumbs />

        <hr class="m-0">

        <Editor
            :metadata
            @update-metadata="(k, v) => emit('updateMetadata', {[k]: v})"
            @update-task="(yaml) => emit('updateTask', yaml)"
            @reorder="(yaml) => emit('reorder', yaml)"
            @update-documentation="(task) => emit('updateDocumentation', task)"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, provide, ref} from "vue";
    import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";

    import {BREADCRUMB_INJECTION_KEY, CLOSE_TASK_FUNCTION_INJECTION_KEY, CREATE_TASK_FUNCTION_INJECTION_KEY, CREATING_TASK_INJECTION_KEY, EDIT_TASK_FUNCTION_INJECTION_KEY, FLOW_INJECTION_KEY, PANEL_INJECTION_KEY, POSITION_INJECTION_KEY, SAVEMODE_INJECTION_KEY, SECTION_INJECTION_KEY, TASKID_INJECTION_KEY} from "./injectionKeys";
    import Breadcrumbs from "./components/Breadcrumbs.vue";
    import Editor from "./segments/Editor.vue";

    const emit = defineEmits<{
        (e: "updateTask", yaml: string): void
        (e: "updateMetadata", value: {[key: string]: any}): void
        (e: "updateDocumentation", task: string): void
        (e: "reorder", yaml: string): void
        (e: "createTask", section: string): boolean | void
        (e: "editTask", section: string, taskId: string): boolean | void
        (e: "closeTask"): boolean | void
    }>()

    const props = withDefaults(
        defineProps<{
            flow: string;
            saveMode?: "button" | "auto";
            /**
             * Initial section name when opening
             * a no-code panel from topology
             */
            section?: string;
            /**
             * Initial task id when opening
             * a no-code panel from topology
             */
            taskId?: string;
            creatingTask?: boolean;
            position?: "before" | "after";
        }>(), {
            saveMode: "button",
            creatingTask: false,
            position: "after",
            section: "",
            taskId: "",
        });

    const metadata = computed(() => YAML_UTILS.getMetadata(props.flow));

    const injectedSection = ref<string>(props.section)
    const injectedTaskId = ref<string>(props.taskId)

    const creatingTaskRef = ref(props.creatingTask)
    const breadcrumbs = ref([])
    const panel = ref()

    provide(FLOW_INJECTION_KEY, computed(() => props.flow));
    provide(PANEL_INJECTION_KEY, panel)
    provide(BREADCRUMB_INJECTION_KEY, breadcrumbs);
    provide(SECTION_INJECTION_KEY, injectedSection);
    provide(TASKID_INJECTION_KEY, injectedTaskId);
    provide(POSITION_INJECTION_KEY, props.position);
    provide(SAVEMODE_INJECTION_KEY, props.saveMode);
    provide(CREATING_TASK_INJECTION_KEY, computed(() => creatingTaskRef.value));
    provide(CREATE_TASK_FUNCTION_INJECTION_KEY, (section) => {
        if(emit("createTask", section) === false){
            return
        }
        injectedSection.value = section
        creatingTaskRef.value = true
        injectedTaskId.value = ""
    });
    provide(EDIT_TASK_FUNCTION_INJECTION_KEY, (section, taskId) => {
        if(emit("editTask", section, taskId) === false){
            return
        }
        injectedSection.value = section
        creatingTaskRef.value = false
        injectedTaskId.value = taskId
    });
    provide(CLOSE_TASK_FUNCTION_INJECTION_KEY, () => {
        console.log("close task tab", injectedSection.value, injectedTaskId.value)
        if(emit("closeTask") === false){
            return
        }

        if (breadcrumbs.value.length > 2) {
            breadcrumbs.value.pop();
        } else {
            injectedSection.value = "";
            injectedTaskId.value = "";
        }

    })
</script>

<style scoped lang="scss">
@import "./styles/code.scss";
</style>
