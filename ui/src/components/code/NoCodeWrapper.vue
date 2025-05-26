<template>
    <div>
        <NoCode
            :flow="lastValidFlowYaml"
            :parent-path="parentPath"
            :ref-path="refPath"
            :block-type="blockType"
            :creating-task="creatingTask"
            :position
            @update-metadata="(e) => onUpdateMetadata(e)"
            @update-task="(e) => editorUpdate(e)"
            @reorder="(yaml) => handleReorder(yaml)"
            @create-task="(blockType, parentPath, refPath) => emit('createTask', blockType, parentPath, refPath, 'after')"
            @close-task="() => emit('closeTask')"
            @edit-task="(blockType, parentPath, refPath) => emit('editTask', blockType, parentPath, refPath)"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue";
    import debounce from "lodash/debounce";
    import {useStore} from "vuex";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import NoCode from "./NoCode.vue";
    import {BlockType} from "./utils/types";

    export interface NoCodeProps {
        creatingTask?: boolean;
        blockType?: BlockType | "pluginDefaults";
        parentPath?: string;
        refPath?: number;
        position?: "before" | "after";
    }

    defineProps<NoCodeProps>();

    const emit = defineEmits<{
        (e: "createTask", blockType: string, parentPath: string, refPath: number | undefined, position: "after" | "before"): boolean | void;
        (e: "editTask", blockType: string, parentPath: string, refPath: number): boolean | void;
        (e: "closeTask"): boolean | void;
    }>();

    const store = useStore();
    const flowYaml = computed<string>(() => store.getters["flow/flowYaml"]);

    const lastValidFlowYaml = computed<string>(
        (oldValue) => {
            try {
                YAML_UTILS.parse(flowYaml.value);
                return flowYaml.value;
            } catch {
                return oldValue ?? "";
            }
        }
    );

    const validateFlow = debounce(() => {
        store.dispatch("flow/validateFlow", {flow: flowYaml.value});
    }, 500);

    const onUpdateMetadata = (metadata: any) => {
        store.commit("flow/setMetadata", {
            ...metadata.value,
            ...((metadata.concurrency?.limit ?? -1) === 0 ? {
                concurrency: null
            } : metadata)});
        store.dispatch("flow/onSaveMetadata");
        validateFlow()
        store.commit("editor/setTabDirty", {
            name: "Flow",
            dirty: true
        });
    };

    const timeout = ref();

    const editorUpdate = (source: string) => {
        store.commit("flow/setFlowYaml", source);
        store.commit("flow/setHaveChange", true);
        validateFlow();
        store.commit("editor/setTabDirty", {
            name: "Flow",
            dirty: true
        });

        // throttle the trigger of the flow update
        clearTimeout(timeout.value);
        timeout.value = setTimeout(() => {
            store.dispatch("flow/onEdit", {
                source,
                currentIsFlow: true,
                topologyVisible: true,
            });
        }, 1000);
    };

    const handleReorder = (source: string) => {
        store.commit("flow/setFlowYaml", source);
        store.commit("flow/setHaveChange", true)
        store.dispatch("flow/save", {content: source});
    };
</script>