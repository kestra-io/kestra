<template>
    <NoCode
        :flow="lastValidFlowYaml"
        @update-metadata="(e) => onUpdateMetadata(e, true)"
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

    const onUpdateMetadata = (metadata: any, shouldSave: boolean) => {
        if(shouldSave) {
            store.commit("flow/setMetadata", {...metadata.value, ...(metadata.concurrency?.limit === 0 ? {concurrency: null} : metadata)});
            store.dispatch("flow/onSaveMetadata");
            store.dispatch("flow/validateFlow", {flow: flowYaml.value});
        } else {
            store.commit("flow/setMetadata", metadata.concurrency?.limit === 0 ?  {concurrency: null} : metadata);
        }
    };

    const editorUpdate = (source: string) => {
        store.commit("flow/setFlowYaml", source);
    };

    const handleReorder = (source: string) => {
        store.commit("flow/setFlowYaml", source);
        store.commit("flow/setHaveChange", true)
        store.dispatch("flow/save", {content: source});
    };

    const updatePluginDocumentation = (event: string | undefined, task: any) => {
        store.dispatch("plugin/updateDocumentation", {event,task});
    };
</script>