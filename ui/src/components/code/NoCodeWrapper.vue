<template>
    <NoCode
        :flow="lastValidFlowYaml"
        save-mode="auto"
        :parent-task-id="parentTaskId"
        :section
        :creating-task="Boolean(createIndex)"
        :position
        :task-id="taskId"
        @update-metadata="(e) => onUpdateMetadata(e)"
        @update-task="(e) => editorUpdate(e)"
        @reorder="(yaml) => handleReorder(yaml)"
        @update-documentation="(task) => updatePluginDocumentation(undefined, task)"
        @create-task="(section, parent) => emit('createTask', section, parent)"
        @close-task="() => emit('closeTask')"
        @edit-task="(section, taskId) => emit('editTask', section, taskId)"
    />
</template>

<script setup lang="ts">
    import {computed, onBeforeUnmount, provide, ref, watch} from "vue";
    import debounce from "lodash/debounce";
    import {useStore} from "vuex";
    import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";
    import NoCode from "./NoCode.vue";
    import {TASK_CREATION_INDEX_INJECTION_KEY} from "./injectionKeys";

    export interface NoCodeProps {
        createIndex?: number;
        parentTaskId?: string;
        section?: string;
        taskId?: string;
        position?: "before" | "after";
    }

    const props = defineProps<NoCodeProps>();

    const emit = defineEmits<{
        (e: "createTask", section: string, parentTaskId?:string): boolean | void;
        (e: "editTask", section: string, taskId: string): boolean | void;
        (e: "updateTaskId", newTaskId: string): boolean | void;
        (e: "closeTask"): boolean | void;
    }>();

    const store = useStore();
    const flowYaml = computed<string>(() => store.getters["flow/flowYaml"]);

    watch(
        flowYaml,
        (newValue, oldValue) => {
            const IdLineRE = /id:\s*(\S+)/

            // get the changed lines having an id to emit
            // an event so no-code can stay in sync
            const oldLines = oldValue.split("\n");
            const newLines = newValue.split("\n");
            const oldLinesWithId = oldLines.map((line, index) => ({line, index})).filter(({line}) => IdLineRE.test(line));
            const changedLines = oldLinesWithId.filter(({line, index}) => IdLineRE.test(newLines[index]) && line !== newLines[index]);
            if(changedLines.length > 0){
                for(const {line, index} of changedLines){
                    const oldId = line.match(IdLineRE)?.[1];
                    const newId = newLines[index].match(IdLineRE)?.[1];
                    if(oldId && newId && oldId !== newId && props.taskId === oldId){
                        emit("updateTaskId", newId);
                    }
                }
            }
        }
    );

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

    const updatePluginDocumentation = (event: string | undefined, task: any) => {
        store.dispatch("plugin/updateDocumentation", {event, task});
    };

    onBeforeUnmount(() => {
        if(props.createIndex){
            store.commit("flow/setCreatedTaskYaml", {
                section: props.section,
                index: props.createIndex,
                yaml: undefined
            });
        }
    });

    provide(TASK_CREATION_INDEX_INJECTION_KEY, computed(() => props.createIndex ?? 0));
</script>