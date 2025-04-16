<template>
    <div class="h-100 overflow-y-auto no-code">
        <Breadcrumbs :flow="flowBreadcrumbs" />

        <hr class="m-0">

        <Editor
            :creation="
                route.query.identifier === 'new' ||
                    route.name === 'flows/create'
            "
            :flow
            :metadata
            @update-metadata="(k, v) => emits('updateMetadata', {[k]: v})"
            @update-task="(yaml) => emits('updateTask', yaml)"
            @reorder="(yaml) => emits('reorder', yaml)"
            @update-documentation="(task) => emits('updateDocumentation', task)"
        />
    </div>
</template>

<script setup lang="ts">
    import {onBeforeMount, computed, provide} from "vue";
    import {useRouter, useRoute} from "vue-router";
    import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";

    import {CREATING_INJECTION_KEY, FLOW_INJECTION_KEY, SAVEMODE_INJECTION_KEY} from "./injectionKeys";
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
        }>(), {
            saveMode: "button",
        });

    const flowBreadcrumbs = computed(() => YAML_UTILS.parse(props.flow) as Record<string, string>)
    const metadata = computed(() => YAML_UTILS.getMetadata(props.flow));


    const router = useRouter();
    const route = useRoute();

    provide(FLOW_INJECTION_KEY, props.flow);
    provide(SAVEMODE_INJECTION_KEY, props.saveMode);
    provide(CREATING_INJECTION_KEY, route.query.identifier === "new" ||
        route.name === "flows/create");

    onBeforeMount(async () => {
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        const {section, identifier, type, ...rest} = route.query;
        router.replace({query: {...rest}});
    });
</script>

<style scoped lang="scss">
@import "./styles/code.scss";
</style>
