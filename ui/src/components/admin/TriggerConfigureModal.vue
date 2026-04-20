<template>
    <el-dialog
        v-model="visible"
        width="45rem"
        destroyOnClose
        appendToBody
        :showClose="false"
        class="trigger-configure-modal"
    >
        <template #header>
            <div class="modal-header">
                <div class="header-main">
                    <div class="header-icon" :class="{'header-icon--mcp': isMcp}">
                        <TaskIcon :cls="trigger.type" :icons="pluginsStore.icons" onlyIcon />
                    </div>
                    <div class="header-text">
                        <div class="header-title-row">
                            <h2 class="header-title">
                                {{ displayName }}
                            </h2>
                            <span v-if="trigger.ee" class="ee-badge">EE</span>
                        </div>
                        <code class="header-fqcn">{{ trigger.type }}</code>
                    </div>
                </div>
                <el-button link class="p-1" @click="$emit('cancel')">
                    <Close />
                </el-button>
            </div>
        </template>

        <el-tabs v-model="activeTab" class="modal-tabs">
            <el-tab-pane :label="$t('triggers.add.modal.tab.form')" name="form" class="tab-panel">
                <div class="form-panel">
                    <el-form labelPosition="top" :model="formModel">
                        <el-form-item :label="$t('namespace')" required>
                            <el-select
                                v-model="formModel.namespace"
                                filterable
                                remote
                                :remoteMethod="searchNamespaces"
                                :loading="namespacesLoading"
                                :placeholder="$t('triggers.add.modal.namespace_placeholder')"
                                @change="onNamespaceChange"
                            >
                                <el-option v-for="ns in namespaceOptions" :key="ns" :label="ns" :value="ns" />
                            </el-select>
                        </el-form-item>

                        <el-form-item :label="$t('flow')" required>
                            <el-select
                                v-model="formModel.flowId"
                                filterable
                                :placeholder="$t('triggers.add.modal.flow_placeholder')"
                                :disabled="!formModel.namespace"
                                :loading="flowsLoading"
                            >
                                <el-option v-for="f in flowOptions" :key="f.id" :label="f.id" :value="f.id" />
                            </el-select>
                        </el-form-item>

                        <el-form-item :label="$t('triggers.add.modal.trigger_id')" required>
                            <el-input
                                v-model="formModel.triggerId"
                                :placeholder="$t('triggers.add.modal.trigger_id_placeholder')"
                            />
                        </el-form-item>
                    </el-form>

                    <p class="form-hint">
                        {{ $t("triggers.add.modal.properties_hint") }}
                    </p>
                </div>
            </el-tab-pane>

            <el-tab-pane :label="$t('triggers.add.modal.tab.source')" name="source" class="tab-panel">
                <div class="source-panel">
                    <div class="editor-wrapper">
                        <el-button size="small" class="copy-button" @click="copySource">
                            <CheckIcon v-if="copied" class="copy-icon text-success" />
                            <ContentCopy v-else class="copy-icon" />
                            <span>{{ copied ? $t("copied") : $t("copy") }}</span>
                        </el-button>
                        <Editor :modelValue="sourceYaml" lang="yaml" :navbar="false" readOnly :fullHeight="false" />
                    </div>
                </div>
            </el-tab-pane>

            <el-tab-pane :label="$t('triggers.add.modal.tab.documentation')" name="documentation" class="tab-panel">
                <div class="doc-panel">
                    <PluginDocumentation
                        v-if="documentationPlugin"
                        :plugin="documentationPlugin"
                        fetchPluginDocumentation
                    />
                    <el-skeleton v-else :rows="6" animated />
                </div>
            </el-tab-pane>
        </el-tabs>

        <template #footer>
            <div class="modal-footer">
                <el-button link @click="$emit('cancel')">
                    {{ $t("cancel") }}
                </el-button>
                <el-button type="primary" :disabled="!canSubmit" @click="addTriggerToFlow">
                    + {{ $t("triggers.add.modal.add_button") }}
                </el-button>
            </div>
        </template>
    </el-dialog>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue";
    import {useRouter} from "vue-router";

    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import CheckIcon from "vue-material-design-icons/Check.vue";
    import Close from "vue-material-design-icons/Close.vue";
    import {TaskIcon} from "@kestra-io/ui-libs";

    import {useFlowStore} from "../../stores/flow";
    import {usePluginsStore, type TriggerPluginDto, type PluginComponent} from "../../stores/plugins";
    import {useNamespacesStore} from "override/stores/namespaces";
    import {useTriggerDraftStore} from "../../stores/triggerDraft";
    import {isMcpTrigger, triggerDisplayName} from "./triggerCatalog";

    import Editor from "../inputs/Editor.vue";
    import PluginDocumentation from "../plugins/PluginDocumentation.vue";

    const visible = defineModel<boolean>("visible", {required: true});
    const props = defineProps<{trigger: TriggerPluginDto}>();
    defineEmits<{(e: "cancel"): void}>();

    const router = useRouter();
    const flowStore = useFlowStore();
    const pluginsStore = usePluginsStore();
    const namespacesStore = useNamespacesStore();
    const triggerDraftStore = useTriggerDraftStore();

    const activeTab = ref<"form" | "source" | "documentation">("form");
    const copied = ref(false);
    const namespacesLoading = ref(false);
    const flowsLoading = ref(false);

    const namespaceOptions = ref<string[]>([]);
    const flowOptions = ref<{id: string; namespace: string}[]>([]);
    const documentationPlugin = ref<PluginComponent | null>(null);

    const generateId = () => `mytrigger_${Math.floor(10000 + Math.random() * 90000)}`;
    const formModel = ref({
        namespace: "",
        flowId: "",
        triggerId: generateId()
    });

    const isMcp = computed(() => isMcpTrigger(props.trigger));
    const displayName = computed(() => triggerDisplayName(props.trigger));
    const canSubmit = computed(() => 
        !!formModel.value.namespace && !!formModel.value.flowId && !!formModel.value.triggerId.trim()
    );

    const getTriggerId = () => formModel.value.triggerId.trim() || "mytrigger";
    const sourceYaml = computed(() => `  - id: ${getTriggerId()}\n type: ${props.trigger.type}\n`);

    const searchNamespaces = async (query: string) => {
        namespacesLoading.value = true;
        try {
            await namespacesStore.loadAutocomplete({q: query, existingOnly: true});
            namespaceOptions.value = (namespacesStore.autocomplete ?? []) as string[];
        } finally {
            namespacesLoading.value = false;
        }
    };

    const loadFlows = async (namespace: string) => {
        if (!namespace) return flowOptions.value = [];
        flowsLoading.value = true;
        try {
            const response = await flowStore.findFlows({namespace, size: 200, sort: "id:asc"});
            flowOptions.value = (response?.results ?? []).map((f: any) => ({id: f.id, namespace: f.namespace}));
        } finally {
            flowsLoading.value = false;
        }
    };

    const onNamespaceChange = (ns: string) => {
        formModel.value.flowId = "";
        loadFlows(ns);
    };

    const copySource = async () => {
        await navigator.clipboard.writeText(`triggers:\n${sourceYaml.value}\n`);
        copied.value = true;
        setTimeout(() => copied.value = false, 1600);
    };

    const loadDocumentation = async () => {
        try {
            const doc = await pluginsStore.load({cls: props.trigger.type, commit: false});
            documentationPlugin.value = {...doc, cls: props.trigger.type};
        } catch {
            documentationPlugin.value = null;
        }
    };

    const addTriggerToFlow = () => {
        if (!canSubmit.value) return;

        triggerDraftStore.setDraft({
            namespace: formModel.value.namespace,
            flowId: formModel.value.flowId,
            triggerYaml: `id: ${getTriggerId()}\ntype: ${props.trigger.type}\n`,
        });

        visible.value = false;
        router.push({
            name: "flows/update",
            params: {namespace: formModel.value.namespace, id: formModel.value.flowId, tab: "edit"},
            query: {createTrigger: "true"}
        });
    };

    watch(visible, val => {
        if (val) {
            activeTab.value = "form";
            copied.value = false;
            formModel.value = {namespace: "", flowId: "", triggerId: generateId()};
            searchNamespaces("");
            loadDocumentation();
        }
    }, {immediate: true});
</script>

<style scoped lang="scss">
    $ks-tab-active: var(--el-color-primary);

    .trigger-configure-modal {
        :deep(.el-dialog__header) {
            padding: 0;
            margin: 0;
            border-bottom: 1px solid var(--ks-border-secondary);
        }

        :deep(.el-dialog__body) {
            padding: 0;
        }

        :deep(.el-dialog__footer) {
            padding: 0.75rem 1.25rem;
            border-top: 1px solid var(--ks-border-secondary);
        }
    }

    .modal-header {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 0.75rem;
        padding: 1rem 1.25rem 0;

        .header-main {
            display: flex;
            align-items: center;
            gap: 0.75rem;
            min-width: 0;
            flex: 1;
        }

        .header-icon {
            width: 2.5rem;
            height: 2.5rem;
            border-radius: 0.5rem;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            flex-shrink: 0;
            background: color-mix(in srgb, #{$ks-tab-active} 18%, transparent);
            color: #{$ks-tab-active};

            :deep(img), :deep(svg) {
                width: 1.375rem;
                height: 1.375rem;
            }

            &--mcp {
                $mcp-color: #ec4899;
                background: color-mix(in srgb, #{$mcp-color} 18%, transparent);
                color: $mcp-color;
            }
        }

        .header-text {
            min-width: 0;
            flex: 1;
        }

        .header-title-row {
            display: flex;
            align-items: center;
            gap: 0.5rem;
        }

        .header-title {
            margin: 0;
            font-size: 1rem;
            font-weight: 600;
            color: var(--ks-content-primary);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .ee-badge {
            font-size: 0.625rem;
            font-weight: 600;
            letter-spacing: 0.05em;
            padding: 1px 0.375rem;
            border-radius: 3px;
            color: #{$ks-tab-active};
            background: color-mix(in srgb, #{$ks-tab-active} 18%, transparent);
        }

        .header-fqcn {
            display: block;
            margin-top: 2px;
            font-size: 0.75rem;
            font-family: var(--font-monospace);
            color: var(--ks-content-secondary);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
    }

    .modal-tabs {
        :deep(.el-tabs__header) {
            margin: 0;
            padding: 0 1.25rem;
            border-bottom: 1px solid var(--ks-border-secondary);
        }

        :deep(.el-tabs__nav-wrap::after) {
            display: none;
        }

        :deep(.el-tabs__item) {
            padding: 0 1rem;
            font-size: 0.8125rem;
            font-weight: 500;
            height: 2.5rem;
            line-height: 2.5rem;
            color: var(--ks-content-secondary);

            &:hover {
                color: var(--ks-content-primary);
            }

            &.is-active {
                color: var(--el-color-primary);
            }
        }
    }

    .tab-panel {
        padding: 1rem 1.25rem 1.25rem;
    }

    .form-panel {
        :deep(.el-form-item):first-of-type {
            margin-top: 0;
        }

        .form-hint {
            margin-top: 0.5rem;
            margin-bottom: 0;
            font-size: 0.8125rem;
            color: var(--ks-content-secondary);
        }
    }

    .source-panel {
        display: flex;
        flex-direction: column;

        .editor-wrapper {
            position: relative;
            border: 1px solid var(--ks-border-primary);
            border-radius: 0.375rem;
            overflow: hidden;
            height: 5rem;

            :deep(.monaco-editor),
            :deep(.monaco-editor .overflow-guard) {
                height: 100% !important;
            }
        }

        .copy-button {
            position: absolute;
            top: 0.375rem;
            right: 0.375rem;
            z-index: 2;
        }

        .copy-icon {
            display: inline-flex;
            font-size: 0.8125rem;
        }
    }

    .doc-panel :deep(.plugin-doc) {
        max-width: 100%;
        background: transparent !important;
    }

    .modal-footer {
        display: flex;
        justify-content: space-between;
        gap: 0.5rem;
    }
</style>
