<template>
    <el-dialog
        v-model="visibleProxy"
        :title="modalTitle"
        width="720"
        destroyOnClose
        appendToBody
        class="trigger-configure-modal"
    >
        <el-alert
            v-if="trigger.ee"
            type="warning"
            :closable="false"
            show-icon
            class="ee-notice"
        >
            {{ $t("triggers.add.modal.ee_notice") }}
        </el-alert>

        <el-tabs v-model="activeTab" class="modal-tabs">
            <el-tab-pane :label="$t('triggers.add.modal.tab.form')" name="form">
                <el-form label-position="top" :model="formModel">
                    <el-form-item :label="$t('namespace')" required>
                        <el-select
                            v-model="formModel.namespace"
                            filterable
                            remote
                            :remote-method="searchNamespaces"
                            :loading="namespacesLoading"
                            :placeholder="$t('triggers.add.modal.namespace_placeholder')"
                            @change="onNamespaceChange"
                        >
                            <el-option
                                v-for="ns in namespaceOptions"
                                :key="ns"
                                :label="ns"
                                :value="ns"
                            />
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
                            <el-option
                                v-for="f in flowOptions"
                                :key="f.id"
                                :label="f.id"
                                :value="f.id"
                            />
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
            </el-tab-pane>

            <el-tab-pane :label="$t('triggers.add.modal.tab.source')" name="source">
                <div class="source-tab">
                    <div class="source-header">
                        <span class="source-label">triggers:</span>
                        <el-button
                            size="small"
                            :icon="ContentCopy"
                            @click="copySource"
                        >
                            {{ $t("copy") }}
                        </el-button>
                    </div>
                    <pre class="source-yaml"><code>{{ previewYaml }}</code></pre>
                </div>
            </el-tab-pane>

            <el-tab-pane :label="$t('triggers.add.modal.tab.documentation')" name="documentation" lazy>
                <PluginDocumentation
                    v-if="documentationPlugin"
                    :plugin="documentationPlugin"
                    :fetchPluginDocumentation="true"
                />
                <el-skeleton v-else :rows="6" animated />
            </el-tab-pane>
        </el-tabs>

        <template #footer>
            <div class="modal-footer">
                <el-button link @click="$emit('cancel')">
                    {{ $t("cancel") }}
                </el-button>
                <el-button
                    type="primary"
                    :disabled="!canSubmit"
                    @click="addTriggerToFlow"
                >
                    + {{ $t("triggers.add.modal.add_button") }}
                </el-button>
            </div>
        </template>
    </el-dialog>
</template>

<script setup lang="ts">
    import {computed, onMounted, ref, watch} from "vue";
    import {useRouter} from "vue-router";
    import {useI18n} from "vue-i18n";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";

    import {useFlowStore} from "../../stores/flow";
    import {usePluginsStore, type TriggerPluginDto, type PluginComponent} from "../../stores/plugins";
    import {useNamespacesStore} from "override/stores/namespaces";
    import {useTriggerDraftStore} from "../../stores/triggerDraft";
    import {useToast} from "../../utils/toast";
    import PluginDocumentation from "../plugins/PluginDocumentation.vue";

    const props = defineProps<{
        visible: boolean;
        trigger: TriggerPluginDto;
    }>();

    const emit = defineEmits<{
        (e: "update:visible", value: boolean): void;
        (e: "cancel"): void;
    }>();

    const router = useRouter();
    const toast = useToast();
    const {t} = useI18n({useScope: "global"});
    const flowStore = useFlowStore();
    const pluginsStore = usePluginsStore();
    const namespacesStore = useNamespacesStore();
    const triggerDraftStore = useTriggerDraftStore();

    const visibleProxy = computed({
        get: () => props.visible,
        set: value => emit("update:visible", value),
    });

    const activeTab = ref<"form" | "source" | "documentation">("form");

    const formModel = ref({
        namespace: "",
        flowId: "",
        triggerId: generateDefaultTriggerId(),
    });

    const namespaceOptions = ref<string[]>([]);
    const namespacesLoading = ref(false);
    const flowOptions = ref<{id: string; namespace: string}[]>([]);
    const flowsLoading = ref(false);
    const documentationPlugin = ref<PluginComponent | null>(null);

    const modalTitle = computed(() =>
        t("triggers.add.modal.title", {name: props.trigger.name})
    );

    const canSubmit = computed(() =>
        Boolean(formModel.value.namespace && formModel.value.flowId && formModel.value.triggerId.trim())
    );

    const previewYaml = computed(() => buildTriggerYaml(formModel.value.triggerId, props.trigger.type));

    function generateDefaultTriggerId(): string {
        const suffix = Math.floor(10000 + Math.random() * 90000);
        return `mytrigger_${suffix}`;
    }

    function buildTriggerYaml(id: string, type: string): string {
        const safeId = id.trim() || "mytrigger";
        return `- id: ${safeId}\n  type: ${type}\n`;
    }

    async function searchNamespaces(query: string) {
        namespacesLoading.value = true;
        try {
            await namespacesStore.loadAutocomplete({q: query, existingOnly: true});
            namespaceOptions.value = (namespacesStore.autocomplete ?? []) as string[];
        } finally {
            namespacesLoading.value = false;
        }
    }

    async function loadFlowsForNamespace(namespace: string) {
        if (!namespace) {
            flowOptions.value = [];
            return;
        }
        flowsLoading.value = true;
        try {
            const response = await flowStore.findFlows({namespace, size: 200, sort: "id:asc"});
            flowOptions.value = (response?.results ?? []).map((f: any) => ({id: f.id, namespace: f.namespace}));
        } finally {
            flowsLoading.value = false;
        }
    }

    function onNamespaceChange(namespace: string) {
        formModel.value.flowId = "";
        loadFlowsForNamespace(namespace);
    }

    async function copySource() {
        try {
            await navigator.clipboard.writeText(previewYaml.value);
            toast.saved(t("copied_to_clipboard"));
        } catch (_err) {
            toast.error(t("copy_failed"));
        }
    }

    async function loadDocumentation() {
        try {
            const doc = await pluginsStore.load({cls: props.trigger.type, commit: false});
            documentationPlugin.value = {...doc, cls: props.trigger.type};
        } catch (_err) {
            documentationPlugin.value = null;
        }
    }

    function addTriggerToFlow() {
        if (!canSubmit.value) return;

        triggerDraftStore.setDraft({
            namespace: formModel.value.namespace,
            flowId: formModel.value.flowId,
            triggerYaml: buildTriggerYaml(formModel.value.triggerId, props.trigger.type),
        });

        emit("update:visible", false);

        router.push({
            name: "flows/update",
            params: {
                namespace: formModel.value.namespace,
                id: formModel.value.flowId,
                tab: "edit",
            },
            query: {
                createTrigger: "true",
            },
        });
    }

    watch(() => props.visible, visible => {
        if (visible) {
            activeTab.value = "form";
            formModel.value = {
                namespace: "",
                flowId: "",
                triggerId: generateDefaultTriggerId(),
            };
            searchNamespaces("");
            loadDocumentation();
        }
    }, {immediate: true});

    onMounted(() => {
        if (props.visible) {
            searchNamespaces("");
            loadDocumentation();
        }
    });
</script>

<style scoped lang="scss">
    .trigger-configure-modal :deep(.el-dialog__body) {
        padding-top: 0;
    }

    .ee-notice {
        margin-bottom: 1rem;
    }

    .modal-tabs {
        min-height: 360px;
    }

    .form-hint {
        margin-top: 1rem;
        font-size: .85rem;
        color: var(--bs-gray-600);
    }

    .source-tab {
        display: flex;
        flex-direction: column;
        gap: .5rem;
    }

    .source-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
    }

    .source-label {
        font-family: var(--font-monospace, monospace);
        color: var(--bs-emphasis-color);
        font-weight: 600;
    }

    .source-yaml {
        background: var(--bs-gray-100);
        border-radius: 6px;
        padding: 1rem;
        font-family: var(--font-monospace, monospace);
        font-size: .85rem;
        white-space: pre-wrap;
        overflow-x: auto;
        margin: 0;
    }

    .modal-footer {
        display: flex;
        justify-content: space-between;
        gap: .5rem;
    }
</style>
