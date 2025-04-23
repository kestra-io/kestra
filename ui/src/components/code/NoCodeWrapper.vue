<template>
    <KeepAlive>
        <NoCode
            :flow="lastValidFlowYaml"
            save-mode="auto"
            :section
            :creating-task
            :position
            :task-id="taskId"
            @update-metadata="(e) => onUpdateMetadata(e)"
            @update-task="(e) => editorUpdate(e)"
            @reorder="(yaml) => handleReorder(yaml)"
            @update-documentation="(task) => updatePluginDocumentation(undefined, task)"
            @create-task="(section) => emit('createTask', section)"
            @edit-task="(section, taskId) => emit('editTask', section, taskId)"
        />
    </KeepAlive>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {useStore} from "vuex";
    import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";
    import NoCode from "./NoCode.vue";

    export interface NoCodeProps {
        creatingTask?: boolean;
        section?: string;
        taskId?: string;
        position?: "before" | "after";
    }

    defineProps<NoCodeProps>();

    const emit = defineEmits<{
        (e: "createTask", section: string): boolean | void;
        (e: "editTask", section: string, taskId: string): boolean | void;
    }>();

    const store = useStore();
    const flowYaml = computed(() => store.getters["flow/flowYaml"]);

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