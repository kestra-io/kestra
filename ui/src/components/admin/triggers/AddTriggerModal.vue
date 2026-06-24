<template>
    <KsDialog
        v-model="visible"
        destroyOnClose
        appendToBody
        :large="activeTab === 'documentation'"
    >
        <template #header>
            <div class="header">
                <div class="title">
                    <h2>{{ $t("add") }} {{ displayName }}</h2>
                    <KsTag v-if="trigger.ee" type="warning" size="small">
                        EE
                    </KsTag>
                </div>
                <code class="type">{{ trigger.type }}</code>
            </div>
        </template>

        <div class="modal-body">
            <KsTabs v-model="activeTab" type="segmented">
                <KsTabPane name="form" :label="$t('triggers_add_modal_tab_form')">
                    <div class="form">
                        <KsForm labelPosition="left" labelWidth="122px" :model="formModel">
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
                                    class="id-input"
                                    :placeholder="$t('triggers_add_modal_trigger_id_placeholder')"
                                />
                            </KsFormItem>
                        </KsForm>

                        <p class="hint">
                            {{ $t("triggers_add_modal_properties_hint") }}
                        </p>
                    </div>
                </KsTabPane>

                <KsTabPane name="source" :label="$t('triggers_add_modal_tab_source')">
                    <div class="source">
                        <KsIconButton
                            class="copy"
                            :aria-label="copied ? $t('copied') : $t('copy')"
                            @click="copySource"
                        >
                            <CheckIcon v-if="copied" class="text-success" />
                            <ContentCopy v-else />
                        </KsIconButton>
                        <KsEditor
                            v-bind="editorBindings"
                            :modelValue="sourceYaml"
                            lang="yaml"
                            :inline="true"
                            :navbar="false"
                            readOnly
                            :options="{
                                fullHeight: false,
                                customHeight: 24,
                                editor: {
                                    padding: {top: 6, bottom: 6},
                                    guides: {indentation: false},
                                },
                            }"
                        />
                    </div>
                </KsTabPane>

                <KsTabPane name="documentation" :label="$t('triggers_add_modal_tab_documentation')">
                    <div class="docs">
                        <PluginDocumentation
                            v-if="documentationPlugin"
                            :plugin="documentationPlugin"
                            fetchPluginDocumentation
                        />
                        <KsSkeleton v-else :rows="6" animated />
                    </div>
                </KsTabPane>
            </KsTabs>
        </div>

        <template #footer>
            <div class="footer">
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
    import {useRouter} from "vue-router"

    import {KsEditor} from "@kestra-io/design-system"
    import CheckIcon from "vue-material-design-icons/Check.vue"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"

    import {useFlowStore} from "../../../stores/flow"
    import {usePluginsStore, type TriggerPluginDto, type PluginComponent} from "../../../stores/plugins"
    import {useTriggerDraftStore} from "../../../stores/triggerDraft"
    import {useEditorBindings} from "../../../composables/useEditorBindings"
    import {triggerDisplayName} from "./triggerCatalog"

    import NamespaceSelect from "../../namespaces/components/NamespaceSelect.vue"
    import PluginDocumentation from "../../plugins/PluginDocumentation.vue"

    const visible = defineModel<boolean>("visible", {required: true})
    const props = defineProps<{trigger: TriggerPluginDto}>()
    defineEmits<{cancel: []}>()

    const COPY_FEEDBACK_MS = 1600
    const TAB_VALUES = ["form", "source", "documentation"] as const
    type TabValue = typeof TAB_VALUES[number];

    const router = useRouter()
    const flowStore = useFlowStore()
    const pluginsStore = usePluginsStore()
    const triggerDraftStore = useTriggerDraftStore()

    const editorBindings = useEditorBindings()

    const activeTab = ref<TabValue>("form")
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
    const sourceYaml = computed(() => `  - id: ${getTriggerId()}\n    type: ${props.trigger.type}`)

    const loadFlows = async (namespace: string) => {
        if (!namespace) {
            flowOptions.value = []
            return
        }
        flowsLoading.value = true
        try {
            const response = await flowStore.findFlows({"filters[namespace][EQUALS]": namespace, sort: "id:asc"})
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
    .header {
        .title {
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-2);

            h2 {
                min-width: 0;
                margin: 0;
                overflow: hidden;
                font-size: var(--ks-font-size-lg);
                font-weight: var(--ks-font-weight-bold);
                color: var(--ks-text-primary);
                white-space: nowrap;
                text-overflow: ellipsis;
            }
        }

        .type {
            display: block;
            margin-top: var(--ks-spacing-1);
            overflow: hidden;
            font-size: var(--ks-font-size-xs);
            font-family: var(--ks-font-family-mono);
            color: var(--ks-text-secondary);
            white-space: nowrap;
            text-overflow: ellipsis;
        }
    }

    .modal-body :deep(.kel-tabs--segmented) {
        .kel-tabs__header {
            margin: var(--ks-spacing-3) 0 var(--ks-spacing-5);
        }
    }

    .form {
        :deep(.kel-form-item):first-of-type {
            margin-top: 0;
        }

        :deep(.kel-form-item__label) {
            font-weight: var(--ks-font-weight-semibold);
        }

        :deep(.id-input .kel-input__inner) {
            font-size: var(--ks-font-size-xs);
            font-weight: var(--ks-font-weight-regular);
        }

        .hint {
            margin: var(--ks-spacing-4) 0;
            font-size: var(--ks-font-size-2xs);
            color: var(--ks-text-secondary);
        }
    }

    .source {
        position: relative;

        .copy {
            position: absolute;
            top: var(--ks-spacing-2);
            right: var(--ks-spacing-2);
            z-index: 2;
        }
    }

    .docs :deep(.plugin-doc) {
        max-width: 100%;
        background: transparent !important;
    }

    .footer {
        display: flex;
        justify-content: flex-end;
    }
</style>
