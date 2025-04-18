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
            creating?: boolean;
            position?: "before" | "after";
        }>(), {
            saveMode: "button",
            creating: false,
            position: "after",
        });

    const flowBreadcrumbs = computed(() => YAML_UTILS.parse<{id:string}>(props.flow) ?? {id: ""});
    const metadata = computed(() => YAML_UTILS.getMetadata(props.flow));


    const section = ref<string>("")
    const taskId = ref<string>("")


    provide(FLOW_INJECTION_KEY, props.flow);
    provide(SECTION_INJECTION_KEY, section);
    provide(TASKID_INJECTION_KEY, taskId);
    provide(POSITION_INJECTION_KEY, props.position);
    provide(SAVEMODE_INJECTION_KEY, props.saveMode);
    provide(CREATING_INJECTION_KEY, taskId.value === "new" || props.creating);
</script>

<style scoped lang="scss">
@import "./styles/code.scss";
</style>
