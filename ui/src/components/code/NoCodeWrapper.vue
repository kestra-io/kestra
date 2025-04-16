<template>
    <NoCode
        :flow="lastValidFlowYaml"
        save-mode="auto"
        @update-metadata="(e) => onUpdateMetadata(e)"
        @update-task="(e) => editorUpdate(e)"
        @reorder="(yaml) => handleReorder(yaml)"
        @update-documentation="(task) => updatePluginDocumentation(undefined, task)"
    />
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue";
    import {useStore} from "vuex";
    import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";
    import NoCode from "./NoCode.vue";

    const store = useStore();
    const flowYaml = computed(() => store.getters["flow/flowYaml"]);

    const lastValidFlowYaml = ref("");

    watch(flowYaml, (newVal) => {
        try {
            YAML_UTILS.parse(flowYaml.value);
            lastValidFlowYaml.value = newVal;
        } catch {
            // do nothing
        }
    }, {immediate: true});

    const onUpdateMetadata = (metadata: any) => {
        store.commit("flow/setMetadata", {
            ...metadata.value,
            ...((metadata.concurrency?.limit ?? -1) === 0 ? {
                concurrency: null
            } : metadata)});
        store.dispatch("flow/onSaveMetadata");
        store.dispatch("flow/validateFlow", {flow: flowYaml.value});
        store.commit("editor/setTabDirty", {
            name: "Flow",
            dirty: true
        });
    };

    const editorUpdate = (source: string) => {
        store.commit("flow/setFlowYaml", source);
        store.commit("flow/setHaveChange", true);
        store.commit("editor/setTabDirty", {
            name: "Flow",
            dirty: true
        });
    };

    const handleReorder = (source: string) => {
        store.commit("flow/setFlowYaml", source);
        store.commit("flow/setHaveChange", true)
        store.dispatch("flow/save", {content: source});
    };

    const updatePluginDocumentation = (event: string | undefined, task: any) => {
        store.dispatch("plugin/updateDocumentation", {event, task});
    };
</script>