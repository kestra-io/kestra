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
        <template #absolute>
            <KeyShortcuts />
        </template>
    </editor>
</template>

<script setup>
    import {computed} from "vue";
    import {useStore} from "vuex";
    import Editor from "./Editor.vue";
    import KeyShortcuts from "./KeyShortcuts.vue";

    const store = useStore();

    const flow = computed(() => store.getters["flow/flow"])

    const flowYaml = computed(() => flow.value?.source);
    function editorUpdate(newValue){
        store.commit("flow/updateFlowSource", newValue);
    }

    const currentTab = computed(() => store.state.editor.current);
    const isCurrentTabFlow = computed(() => currentTab.value?.extension === undefined)

    const isReadOnly = computed(() => flow.value?.deleted || !store.getters["flow/isAllowedEdit"] || store.getters["flow/readOnlySystemLabel"])

    defineProps({
        isCreating: {
            type: Boolean,
            default: false
        }
    })
</script>