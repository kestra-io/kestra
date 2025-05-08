<template>
    <editor
        ref="editorDomElement"
        :model-value="source"
        :schema-type="isCurrentTabFlow ? 'flow': undefined"
        :lang="extension === undefined ? 'yaml' : undefined"
        :extension="extension"
        :navbar="false"
        :read-only="isReadOnly"
        :creating="isCreating"
        :path="props.path"
        @update:model-value="editorUpdate"
        @cursor="updatePluginDocumentation"
        @save="save"
        @execute="execute"
    >
        <KeyShortcuts />
    </editor>
</template>

<script lang="ts" setup>
    import {computed, onActivated, onMounted, ref} from "vue";
    import {useStore} from "vuex";
    import Editor from "./Editor.vue";
    import KeyShortcuts from "./KeyShortcuts.vue";

    const store = useStore();

    export interface EditorTabProps{
        name: string,
        path: string,
        extension?: string,
        flow?: boolean,
        dirty?: boolean,
    }

    const props = withDefaults(defineProps<EditorTabProps>(), {
        extension: undefined,
        dirty: false,
        flow: true
    });

    const source = computed(() => {
        return props.flow
            ? store.getters["flow/flowYaml"]
            : store.state.editor.tabs.find((t:any) => t.path === props.path)?.content;
    })

    async function loadFile(){
        if(props.dirty || props.flow){
            return;
        }
        const content = await store.dispatch("namespace/readFile", {
            namespace: namespace.value,
            path: props.path
        })
        store.commit("editor/setTabContent", {
            path: props.path,
            content
        })
    }

    onMounted(() => {
        loadFile()
    });

    onActivated(() => {
        loadFile()
    });

    const editorDomElement = ref<any>(null);

    const namespace = computed(() => store.getters["flow/namespace"]);
    const flowStore = computed(() => store.getters["flow/flow"]);
    const isCreating = computed(() => store.state.flow.isCreating);
    const isCurrentTabFlow = computed(() => props.flow)
    const isReadOnly = computed(() => flowStore.value?.deleted || !store.getters["flow/isAllowedEdit"] || store.getters["flow/readOnlySystemLabel"])

    const timeout = ref<any>(null);

    function editorUpdate(newValue: string){
        if(store.state.editor.tabs.find((t:any) => t.path === props.path)?.content === newValue){
            return;
        }
        if(isCurrentTabFlow.value){
            store.commit("flow/setFlowYaml", newValue);
        }
        store.commit("editor/setTabContent", {
            content: newValue,
            path: props.path
        });
        store.commit("editor/setTabDirty", {
            path: props.path,
            dirty: true
        });

        // throttle the trigger of the flow update
        clearTimeout(timeout.value);
        timeout.value = setTimeout(() => {
            store.dispatch("flow/onEdit", {
                source: newValue,
                currentIsFlow: isCurrentTabFlow.value,
                editorViewType: "YAML", // this is to be opposed to the no-code editor
                topologyVisible: true,
            });
        }, 2000);
    }


    function updatePluginDocumentation(event: string | undefined, task: any){
        store.dispatch("plugin/updateDocumentation", {event,task});
    };

    function save(){
        store.commit("editor/setCurrentTab", store.state.editor.tabs.find((t:any) => t.path === props.path));
        return store.dispatch("flow/save", {
            content: editorDomElement.value.$refs.monacoEditor.value,
        })
    }

    const execute = () => {
        store.commit("flow/executeFlow", true);
    };
</script>