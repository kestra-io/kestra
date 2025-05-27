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
        @save="saveFileContent"
        @execute="execute"
    >
        <template #absolute>
            <KeyShortcuts v-if="isCurrentTabFlow" />
            <ContentSave v-else @click="saveFileContent" />
        </template>
    </editor>
</template>

<script lang="ts" setup>
    import {computed, onActivated, onMounted, ref, provide} from "vue";
    import {useStore} from "vuex";
    import Editor from "./Editor.vue";
    import KeyShortcuts from "./KeyShortcuts.vue";

    import ContentSave from "vue-material-design-icons/ContentSave.vue";


    import {EDITOR_CURSOR_INJECTION_KEY} from "../code/injectionKeys";

    const store = useStore();
    const cursor = ref();
    provide(EDITOR_CURSOR_INJECTION_KEY, cursor);


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
        }, 1000);
    }


    function updatePluginDocumentation(event: string | undefined, task: any){
        store.dispatch("plugin/updateDocumentation", {event,task});
    };

    const saveFileContent =  async ()=>{
        await store.dispatch("namespace/createFile", {
            namespace: namespace.value,
            path: props.path,
            content: editorDomElement.value.modelValue,
        });
        store.commit("editor/setTabDirty", {
            path: props.path,
            dirty: false
        });
    }

    const execute = () => {
        store.commit("flow/executeFlow", true);
    };
</script>