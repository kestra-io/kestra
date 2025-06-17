<template>
    <editor
        id="editorWrapper"
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
        @save="isCurrentTabFlow ? save(): saveFileContent()"
        @execute="execute"
    >
        <template #absolute>
            <KeyShortcuts v-if="isCurrentTabFlow" />
            <ContentSave v-else @click="saveFileContent" />
        </template>
    </editor>
</template>

<script lang="ts" setup>
    import {computed, onActivated, onMounted, ref, provide, onBeforeUnmount} from "vue";
    import {useStore} from "vuex";
    import Editor from "./Editor.vue";
    import KeyShortcuts from "./KeyShortcuts.vue";

    import ContentSave from "vue-material-design-icons/ContentSave.vue";

    import {useRoute, useRouter} from "vue-router";
    const route = useRoute()
    const router = useRouter()

    import {EDITOR_CURSOR_INJECTION_KEY} from "../code/injectionKeys";
    import {usePluginsStore} from "../../stores/plugins";

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
        loadFile();
        window.addEventListener("keydown", handleGlobalSave);
    });

    onActivated(() => {
        loadFile();
    });

    onBeforeUnmount(() => {
        window.removeEventListener("keydown", handleGlobalSave);
    });

    const editorDomElement = ref<any>(null);

    const namespace = computed(() => store.getters["flow/namespace"]);
    const flowStore = computed(() => store.getters["flow/flow"]);
    const isCreating = computed(() => store.state.flow.isCreating);
    const isCurrentTabFlow = computed(() => props.flow)
    const isReadOnly = computed(() => flowStore.value?.deleted || !store.getters["flow/isAllowedEdit"] || store.getters["flow/readOnlySystemLabel"])

    const timeout = ref<any>(null);

    const pluginsStore = usePluginsStore();
    pluginsStore.setVuexStore(store);

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


    function updatePluginDocumentation(event: any, task: string | undefined) {
        pluginsStore.updateDocumentation({event, task});
    };

    const flowParsed = computed(() => store.getters["flow/flowParsed"]);
    const save = async () => {
        clearTimeout(timeout.value);
        const result = await store.dispatch("flow/save", {content: editorDomElement.value.$refs.monacoEditor.value})
        if(result === "redirect_to_update"){
            await router.push({
                name: "flows/update",
                params: {
                    id: flowParsed.value.id,
                    namespace: flowParsed.value.namespace,
                    tab: "edit",
                    tenant: route.params?.tenant,
                },
            });
        }
    };

    const saveFileContent =  async ()=>{
        clearTimeout(timeout.value);
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

    const handleGlobalSave = (event: KeyboardEvent) => {
        if ((event.ctrlKey || event.metaKey) && event.key === "s") {
            event.preventDefault();
            if (isCurrentTabFlow.value) {
                save();
            } else {
                saveFileContent();
            }
        }
    };

    const execute = () => {
        store.commit("flow/executeFlow", true);
    };
</script>