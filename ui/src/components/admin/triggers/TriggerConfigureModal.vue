<template>
    <KsDialog
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
                    <KsTaskIcon class="header-icon" :cls="trigger.type" :icons="pluginsStore.icons" onlyIcon />
                    <div class="header-text">
                        <div class="header-title-row">
                            <h2 class="header-title">
                                {{ displayName }}
                            </h2>
                            <KsTag v-if="trigger.ee" type="info" size="small">
                                EE
                            </KsTag>
                        </div>
                        <code class="header-fqcn">{{ trigger.type }}</code>
                    </div>
                </div>
                <KsIconButton :aria-label="$t('cancel')" @click="$emit('cancel')">
                    <Close />
                </KsIconButton>
            </div>
        </template>

        <div class="modal-body">
            <div class="tab-switcher">
                <KsSegmented v-model="activeTab" :options="tabOptions" />
            </div>

            <div v-show="activeTab === 'form'" class="tab-panel form-panel">
                <KsForm labelPosition="top" :model="formModel">
                    <KsFormItem :label="$t('namespace')" required>
                        <NamespaceSelect
                            v-model="formModel.namespace"
                            :placeholder="$t('triggers_add_modal_namespace_placeholder')"
                            :clearable="false"
                            :autoDefault="false"
                            @update:model-value="onNamespaceChange"
                        />
                    </KsFormItem>

                    <KsFormItem :label="$t('flow')" required>
                        <KsSelect
                            v-model="formModel.flowId"
                            filterable
                            :placeholder="$t('triggers_add_modal_flow_placeholder')"
                            :disabled="!formModel.namespace"
                            :loading="flowsLoading"
                        >
                            <KsOption v-for="f in flowOptions" :key="f.id" :label="f.id" :value="f.id" />
                        </KsSelect>
                    </KsFormItem>

                    <KsFormItem :label="$t('triggers_add_modal_trigger_id')" required>
                        <KsInput
                            v-model="formModel.triggerId"
                            :placeholder="$t('triggers_add_modal_trigger_id_placeholder')"
                        />
                    </KsFormItem>
                </KsForm>

                <p class="form-hint">
                    {{ $t("triggers_add_modal_properties_hint") }}
                </p>
            </div>

            <div v-show="activeTab === 'source'" class="tab-panel source-panel">
                <div class="editor-wrapper">
                    <KsButton size="small" class="copy-button" @click="copySource">
                        <CheckIcon v-if="copied" class="copy-icon text-success" />
                        <ContentCopy v-else class="copy-icon" />
                        <span>{{ copied ? $t("copied") : $t("copy") }}</span>
                    </KsButton>
                    <Editor :modelValue="sourceYaml" lang="yaml" :navbar="false" readOnly :fullHeight="false" />
                </div>
            </div>

            <div v-show="activeTab === 'documentation'" class="tab-panel doc-panel">
                <PluginDocumentation
                    v-if="documentationPlugin"
                    :plugin="documentationPlugin"
                    fetchPluginDocumentation
                />
                <KsSkeleton v-else :rows="6" animated />
            </div>
        </div>

        <template #footer>
            <div class="modal-footer">
                <KsButton @click="$emit('cancel')">
                    {{ $t("cancel") }}
                </KsButton>
                <KsButton type="primary" :disabled="!canSubmit" @click="addTriggerToFlow">
                    + {{ $t("triggers_add_modal_add_button") }}
                </KsButton>
            </div>
        </template>
    </KsDialog>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRouter} from "vue-router"

    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import CheckIcon from "vue-material-design-icons/Check.vue"
    import Close from "vue-material-design-icons/Close.vue"
    import {KsTaskIcon} from "@kestra-io/design-system"

    import {useFlowStore} from "../../../stores/flow"
    import {usePluginsStore, type TriggerPluginDto, type PluginComponent} from "../../../stores/plugins"
    import {useTriggerDraftStore} from "../../../stores/triggerDraft"
    import {triggerDisplayName} from "./triggerCatalog"

    import Editor from "../../inputs/Editor.vue"
    import PluginDocumentation from "../../plugins/PluginDocumentation.vue"
    import NamespaceSelect from "../../namespaces/components/NamespaceSelect.vue"

    const visible = defineModel<boolean>("visible", {required: true})
    const props = defineProps<{trigger: TriggerPluginDto}>()
    defineEmits<{cancel: []}>()

    const COPY_FEEDBACK_MS = 1600
    const TAB_VALUES = ["form", "source", "documentation"] as const
    type TabValue = typeof TAB_VALUES[number];

    const {t} = useI18n({useScope: "global"})
    const router = useRouter()
    const flowStore = useFlowStore()
    const pluginsStore = usePluginsStore()
    const triggerDraftStore = useTriggerDraftStore()

    const activeTab = ref<TabValue>("form")
    const tabOptions = computed(() =>
        TAB_VALUES.map(value => ({value, label: t(`triggers_add_modal_tab_${value}`)})),
    )
    const copied = ref(false)
    const flowsLoading = ref(false)

    const flowOptions = ref<{id: string; namespace: string}[]>([])
    const documentationPlugin = ref<PluginComponent | null>(null)

    const generateId = () => `mytrigger_${Math.floor(10000 + Math.random() * 90000)}`
    const formModel = ref({
        namespace: "",
        flowId: "",
        triggerId: generateId(),
    })

    const displayName = computed(() => triggerDisplayName(props.trigger))
    const canSubmit = computed(() =>
        !!formModel.value.namespace && !!formModel.value.flowId && !!formModel.value.triggerId.trim(),
    )

    const getTriggerId = () => formModel.value.triggerId.trim() || "mytrigger"
    const sourceYaml = computed(() => `  - id: ${getTriggerId()}\n    type: ${props.trigger.type}\n`)

    const loadFlows = async (namespace: string) => {
        if (!namespace) {
            flowOptions.value = []
            return
        }
        flowsLoading.value = true
        try {
            const response = await flowStore.findFlows({namespace, size: 200, sort: "id:asc"})
            flowOptions.value = (response?.results ?? []).map((f: any) => ({id: f.id, namespace: f.namespace}))
        } finally {
            flowsLoading.value = false
        }
    }

    const onNamespaceChange = (ns: string | string[] | undefined) => {
        formModel.value.flowId = ""
        loadFlows(typeof ns === "string" ? ns : "")
    }

    const copySource = async () => {
        await navigator.clipboard.writeText(`triggers:\n${sourceYaml.value}\n`)
        copied.value = true
        setTimeout(() => copied.value = false, COPY_FEEDBACK_MS)
    }

    const loadDocumentation = async () => {
        try {
            const doc = await pluginsStore.load({cls: props.trigger.type, commit: false})
            documentationPlugin.value = {...doc, cls: props.trigger.type}
        } catch {
            documentationPlugin.value = null
        }
    }

    const addTriggerToFlow = () => {
        if (!canSubmit.value) return

        triggerDraftStore.setDraft({
            namespace: formModel.value.namespace,
            flowId: formModel.value.flowId,
            triggerYaml: `id: ${getTriggerId()}\ntype: ${props.trigger.type}\n`,
        })

        visible.value = false
        router.push({
            name: "flows/update",
            params: {namespace: formModel.value.namespace, id: formModel.value.flowId, tab: "edit"},
            query: {createTrigger: "true"},
        })
    }

    watch(visible, val => {
        if (val) {
            activeTab.value = "form"
            copied.value = false
            formModel.value = {namespace: "", flowId: "", triggerId: generateId()}
            loadDocumentation()
        }
    }, {immediate: true})
</script>

<style scoped lang="scss">
    .trigger-configure-modal {
        :deep(.kel-dialog__header) {
            padding: 0;
            margin: 0;
            border-bottom: 1px solid var(--ks-border-secondary);
        }

        :deep(.kel-dialog__body) {
            padding: 0;
        }

        :deep(.kel-dialog__footer) {
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
            width: 2.25rem;
            height: 2.25rem;
            flex-shrink: 0;
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

        .header-fqcn {
            display: block;
            margin-top: 0.125rem;
            font-size: 0.75rem;
            font-family: var(--ks-font-family-mono);
            color: var(--ks-content-secondary);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
    }

    .tab-switcher {
        padding: 0.75rem 1.25rem 0;
    }

    .tab-panel {
        padding: 1rem 1.25rem 1.25rem;
    }

    .form-panel {
        :deep(.kel-form-item):first-of-type {
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
        justify-self: end;
        display: flex;
    }
</style>
