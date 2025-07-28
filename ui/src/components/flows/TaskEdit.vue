<template>
    <component
        :is="component"
        :icon="CodeTags"
        @click="onShow"
        ref="taskEdit"
    >
        <span v-if="component !== 'el-button' && !isHidden">{{ $t("show task source") }}</span>
        <drawer
            v-if="isModalOpen"
            v-model="isModalOpen"
        >
            <template #header>
                <code>{{ taskId || task?.id || $t("add task") }}</code>
            </template>
            <template #footer>
                <div v-loading="isLoading">
                    <ValidationError class="me-2" link :errors="errors" />

                    <el-button
                        :icon="ContentSave"
                        @click="saveTask"
                        v-if="canSave && !readOnly"
                        :disabled="errors && !!errors.length"
                        type="primary"
                    >
                        {{ $t("save task") }}
                    </el-button>
                    <el-alert
                        show-icon
                        :closable="false"
                        class="mb-0 mt-3"
                        v-if="revision && revisions?.length !== revision"
                        type="warning"
                    >
                        <strong>{{ $t("seeing old revision", {revision: revision}) }}</strong>
                    </el-alert>
                </div>
            </template>

            <el-tabs v-model="activeTabs">
                <el-tab-pane v-if="!readOnly" name="form">
                    <template #label>
                        <span>{{ $t("form") }}</span>
                    </template>
                    <task-editor
                        ref="editor"
                        v-model="taskYaml"
                        :section="section"
                        @update:model-value="onInput"
                    />
                </el-tab-pane>
                <el-tab-pane name="source">
                    <template #label>
                        <span>{{ $t("source") }}</span>
                    </template>
                    <editor
                        :read-only="readOnly"
                        ref="editor"
                        @save="saveTask"
                        v-model="taskYaml"
                        :schema-type="section.toLowerCase()"
                        :full-height="false"
                        :navbar="false"
                        lang="yaml"
                        @update:model-value="onInput"
                    />
                </el-tab-pane>
                <el-tab-pane v-if="pluginMarkdown" name="documentation">
                    <template #label>
                        <span>
                            {{ $t("documentation.documentation") }}
                        </span>
                    </template>
                    <div class="documentation">
                        <markdown :source="pluginMarkdown" />
                    </div>
                </el-tab-pane>
            </el-tabs>
        </drawer>
    </component>
</template>

<script setup lang="ts">
    import {ref, computed, watch} from "vue";
    import {useStore} from "vuex";
    import {SECTIONS} from "@kestra-io/ui-libs";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import CodeTags from "vue-material-design-icons/CodeTags.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import Editor from "../inputs/Editor.vue";
    import TaskEditor from "./TaskEditor.vue";
    import Drawer from "../Drawer.vue";
    import {canSaveFlowTemplate} from "../../utils/flowTemplate";
    import Markdown from "../layout/Markdown.vue";
    import ValidationError from "./ValidationError.vue";
    import {usePluginsStore} from "../../stores/plugins";

    // Types
    interface Props {
        component?: string;
        task?: Record<string, any>;
        taskId?: string;
        flowId: string;
        namespace: string;
        revision?: number;
        section?: string;
        emitOnly?: boolean;
        emitTaskOnly?: boolean;
        isHidden?: boolean;
        readOnly?: boolean;
        flowSource?: string;
    }

    // Props definition
    const props = withDefaults(defineProps<Props>(), {
        component: "el-button",
        task: undefined,
        taskId: undefined,
        revision: undefined,
        section: SECTIONS.TASKS,
        emitOnly: false,
        emitTaskOnly: false,
        isHidden: false,
        readOnly: false,
        flowSource: undefined
    });

    // Emits
    const emit = defineEmits<{
        "update:task": [value: string];
        "close": [];
    }>();

    // Store
    const store = useStore();
    const pluginsStore = usePluginsStore();

    // Reactive state
    const taskYaml = ref("");
    const isModalOpen = ref(false);
    const activeTabs = ref(props.readOnly ? "source" : "form");
    const type = ref<string>();
    const revisions = ref<any[]>();
    const timer = ref<ReturnType<typeof setTimeout>>();
    const lastValidatedValue = ref<string | null>(null);

    // Computed properties
    const errors = computed(() => store.state.flow.taskError?.split(/, ?/));
    const pluginMarkdown = computed(() => {
        if (pluginsStore?.plugin?.markdown && YAML_UTILS.parse(taskYaml.value)?.type) {
            return pluginsStore?.plugin.markdown;
        }
        return null;
    });

    const canSave = computed(() => {
        const user = store.state.auth.user;
        return canSaveFlowTemplate(true, user, {namespace: props.namespace}, "flow");
    });

    const isLoading = computed(() => taskYaml.value === undefined);

    const source = computed(() => {
        return props.revision
            ? revisions.value?.[props.revision - 1]?.source
            : store.state.flow?.source;
    });

    // Methods
    const load = async (taskId: string) => {
        if (props.revision) {
            if (!revisions.value?.[props.revision - 1]) {
                revisions.value = await store.dispatch("flow/loadRevisions", {
                    namespace: props.namespace,
                    id: props.flowId,
                    store: false
                });
            }
        }
        return YAML_UTILS.extractBlock({
            section: props.section,
            source: source.value,
            key: taskId,
        });
    };

    const saveTask = () => {
        emit("update:task", taskYaml.value);
        taskYaml.value = "";
        isModalOpen.value = false;
    };

    const onShow = async () => {
        isModalOpen.value = !isModalOpen.value;
        if (props.taskId) {
            taskYaml.value = await load(props.taskId ? props.taskId : props.task?.id) ?? "";
        } else if (props.task) {
            taskYaml.value = YAML_UTILS.stringify(props.task);
        }
        if (props.task?.type) {
            pluginsStore.load({cls: props.task.type});
        }
    };

    const onInput = (value: string) => {
        if (timer.value) {
            clearTimeout(timer.value);
        }
        taskYaml.value = value;

        timer.value = setTimeout(() => {
            if (lastValidatedValue.value !== value) {
                lastValidatedValue.value = value;
                store.dispatch("flow/validateTask", {
                    task: value,
                    section: props.section
                });
            }
        }, 500) as any;
    };

    // Watchers
    watch(() => props.task, async (newTask) => {
        if (newTask) {
            taskYaml.value = YAML_UTILS.stringify(newTask);
            if (newTask.type) {
                await pluginsStore.load({cls: newTask.type});
            }
        } else {
            taskYaml.value = "";
        }
    }, {immediate: true});

    watch(taskYaml, () => {
        const task = YAML_UTILS.parse(taskYaml.value);
        if (task?.type && task.type !== type.value) {
            pluginsStore.load({cls: task.type});
            type.value = task.type;
        }
    });

    watch(isModalOpen, () => {
        if (!isModalOpen.value) {
            emit("close");
            activeTabs.value = props.readOnly ? "source" : "form";
        }
    });
</script>

<style scoped lang="scss">
    // Required, otherwise the doc titles and properties names are not visible
    .documentation {
        padding: 1rem;
    }
</style>
