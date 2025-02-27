<template>
    <editor
        class="position-relative"
        ref="editorDomElement"
        :model-value="flowYaml"
        :schema-type="isCurrentTabFlow ? 'flow': undefined"
        :lang="currentTab?.extension === undefined ? 'yaml' : undefined"
        :extension="currentTab?.extension"
        :navbar="false"
        :read-only="isReadOnly"
        :creating="isCreating"
        @update:model-value="editorUpdate"
        @cursor="updatePluginDocumentation"
        @save="save"
        @execute="execute"
    >
        <KeyShortcuts />
    </editor>
</template>

<script lang="ts" setup>
    import {computed, ref} from "vue";
    import {useStore} from "vuex";
    import Editor from "./Editor.vue";
    import KeyShortcuts from "./KeyShortcuts.vue";

    const store = useStore();

    const editorDomElement = ref<any>(null);

    const flow = computed(() => store.getters["flow/flow"])
    const flowYaml = computed(() => store.getters["flow/flowYaml"]);
    const isCreating = computed(() => store.state.flow.isCreating);

    function editorUpdate(newValue: string){
        store.commit("flow/updateFlowSource", newValue);
    }

    const currentTab = computed(() => store.state.editor.current);
    const isCurrentTabFlow = computed(() => currentTab.value?.extension === undefined)

    const isReadOnly = computed(() => flow.value?.deleted || !store.getters["flow/isAllowedEdit"] || store.getters["flow/readOnlySystemLabel"])

    function updatePluginDocumentation(event: string | undefined, task: any){
        store.dispatch("plugin/updateDocumentation", {event,task});
    };

    function save(){
        return store.dispatch("flow/save", {
            content: editorDomElement.value.$refs.monacoEditor.value,
        })
    }

    const execute = (_) => {
        store.commit("flow/executeFlow", true);
    };
</script>