<template>
    <div class="h-100 overflow-y-auto no-code">
        <Breadcrumbs :flow="flowBreadcrumbs" />

        <hr class="m-0">

        <Editor
            :metadata
            @update-metadata="(k, v) => emits('updateMetadata', {[k]: v})"
            @update-task="(yaml) => emits('updateTask', yaml)"
            @reorder="(yaml) => emits('reorder', yaml)"
            @update-documentation="(task) => emits('updateDocumentation', task)"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, provide, ref} from "vue";
    import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";

    import {CREATING_INJECTION_KEY, FLOW_INJECTION_KEY, POSITION_INJECTION_KEY, SAVEMODE_INJECTION_KEY, SECTION_INJECTION_KEY, TASKID_INJECTION_KEY} from "./injectionKeys";
    import Breadcrumbs from "./components/Breadcrumbs.vue";
    import Editor from "./segments/Editor.vue";

    const emits = defineEmits([
        "updateTask",
        "updateMetadata",
        "updateDocumentation",
        "reorder",
    ]);

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
            creating?: boolean;
            position?: "before" | "after";
        }>(), {
            saveMode: "button",
            creating: false,
            position: "after",
            section: "",
            taskId: "",
        });

    const flowBreadcrumbs = computed(() => YAML_UTILS.parse<{id:string}>(props.flow) ?? {id: ""});
    const metadata = computed(() => YAML_UTILS.getMetadata(props.flow));


    const injectedSection = ref<string>(props.section)
    const injectedTaskId = ref<string>(props.taskId)


    provide(FLOW_INJECTION_KEY, computed(() => props.flow));
    provide(SECTION_INJECTION_KEY, injectedSection);
    provide(TASKID_INJECTION_KEY, injectedTaskId);
    provide(POSITION_INJECTION_KEY, props.position);
    provide(SAVEMODE_INJECTION_KEY, props.saveMode);
    provide(CREATING_INJECTION_KEY, ref(props.creating));
</script>

<style scoped lang="scss">
@import "./styles/code.scss";
</style>
