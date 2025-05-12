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
    import {computed,  inject,  onBeforeUnmount,  provide, ref} from "vue";
    import {useStore} from "vuex";
    import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";

    import {
        BREADCRUMB_INJECTION_KEY, CLOSE_TASK_FUNCTION_INJECTION_KEY,
        CREATE_TASK_FUNCTION_INJECTION_KEY, CREATING_TASK_INJECTION_KEY,
        EDIT_TASK_FUNCTION_INJECTION_KEY, FLOW_INJECTION_KEY,
        PANEL_INJECTION_KEY, POSITION_INJECTION_KEY,
        SAVEMODE_INJECTION_KEY, SECTION_INJECTION_KEY,
        TASKID_INJECTION_KEY, PARENT_TASKID_INJECTION_KEY,
        TASK_CREATION_INDEX_INJECTION_KEY
    } from "./injectionKeys";
    import Breadcrumbs from "./components/Breadcrumbs.vue";
    import Editor from "./segments/Editor.vue";
    import {Breadcrumb} from "./utils/types";

    const store = useStore();

    const emit = defineEmits<{
        (e: "updateTask", yaml: string): void
        (e: "updateMetadata", value: {[key: string]: any}): void
        (e: "updateDocumentation", task: string): void
        (e: "reorder", yaml: string): void
        (e: "createTask", section: string, parentTaskId?: string): boolean | void
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
             * (if it's a pluginDefaults, we have the type instead)
             */
            taskId?: string;
            /**
             * When opening, the taskId of the parent task
             * to add subtasks into
             */
            parentTaskId?: string;
            creatingTask?: boolean;
            position?: "before" | "after";
        }>(), {
            saveMode: "button",
            creatingTask: false,
            position: "after",
            section: "",
            taskId: "",
            parentTaskId: undefined
        });

    const metadata = computed(() => YAML_UTILS.getMetadata(props.flow));

    const injectedSection = ref<string>(props.section)
    const injectedTaskId = ref<string>(props.taskId)

    const creatingTaskRef = ref(props.creatingTask)
    const breadcrumbs = ref<Breadcrumb[]>([])
    const panel = ref()
    const parentTaskIdRef = ref(props.parentTaskId)

    const taskCreationIndex = inject(
        TASK_CREATION_INDEX_INJECTION_KEY,
        ref(0),
    );

    provide(FLOW_INJECTION_KEY, computed(() => props.flow));
    provide(PARENT_TASKID_INJECTION_KEY, parentTaskIdRef);
    provide(PANEL_INJECTION_KEY, panel)
    provide(BREADCRUMB_INJECTION_KEY, breadcrumbs);
    provide(SECTION_INJECTION_KEY, injectedSection);
    provide(TASKID_INJECTION_KEY, injectedTaskId);
    provide(POSITION_INJECTION_KEY, props.position);
    provide(SAVEMODE_INJECTION_KEY, props.saveMode);
    provide(CREATING_TASK_INJECTION_KEY, computed(() => creatingTaskRef.value));
    provide(CREATE_TASK_FUNCTION_INJECTION_KEY, (section) => {
        if(emit("createTask", section, injectedTaskId.value) === false){
            return
        }
        parentTaskIdRef.value = injectedTaskId.value
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
        if (breadcrumbs.value.length > 2) {
            breadcrumbs.value.pop();
        } else {
            // only close the tab if saving a task not a value
            if(emit("closeTask") === false){
                return
            }

            injectedSection.value = "";
            injectedTaskId.value = "";
            creatingTaskRef.value = false
        }

    })

    onBeforeUnmount(() => {
        // cleanup the addition model on close
        if(props.creatingTask) {
            store.commit("flow/setCreatedTaskYaml", {
                section: injectedSection.value,
                index: taskCreationIndex.value - 1,
                yaml: undefined,
            });
        }
    })
</script>

<style scoped lang="scss">
@import "./styles/code.scss";
</style>
