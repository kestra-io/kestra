<template>
    <KsDialog
        :modelValue="true"
        width="min(1400px, 94vw)"
        top="4vh"
        :showClose="false"
        appendToBody
        @close="emit('close')"
    >
        <template #header>
            <div class="task-edit-modal-header">
                <span class="task-edit-modal-title">{{ title }}</span>
                <KsIconButton
                    class="task-edit-modal-header-action"
                    :type="docsOpen ? 'primary' : 'default'"
                    :aria-label="t('documentation.documentation')"
                    :tooltip="t('documentation.documentation')"
                    data-test="task-edit-modal-docs-toggle"
                    @click="toggleDocs"
                >
                    <HelpCircleOutline />
                </KsIconButton>
                <KsIconButton
                    class="task-edit-modal-header-action"
                    :aria-label="t('block_editor.open_in_tabs')"
                    :tooltip="t('block_editor.open_in_tabs')"
                    data-test="task-edit-modal-open-in-tabs"
                    @click="emit('open-in-tabs')"
                >
                    <DockWindow />
                </KsIconButton>
                <KsIconButton
                    class="task-edit-modal-header-action"
                    :aria-label="t('close')"
                    :tooltip="t('close')"
                    data-test="task-edit-modal-close"
                    @click="emit('close')"
                >
                    <Close />
                </KsIconButton>
            </div>
        </template>

        <div class="task-edit-modal-body">
            <div class="task-edit-modal-form">
                <Suspense>
                    <TaskEdit
                        :task="task"
                        :taskRaw="taskRaw"
                        :section="section"
                        :flowId="flowId"
                        :namespace="namespace"
                        :editorKey="editorKey"
                        presentation="panel"
                        :isHidden="true"
                        :hideTabstrip="true"
                        @update:task="emit('update:task', $event)"
                        @close="emit('close')"
                    />
                </Suspense>
            </div>

            <div v-if="docsOpen" class="task-edit-modal-docs" v-ks-loading="docsLoading">
                <Suspense>
                    <PluginDocumentation
                        v-if="docsPlugin"
                        :plugin="docsPlugin"
                    />
                </Suspense>
            </div>
        </div>

        <template v-if="!hintDismissed" #footer>
            <div class="task-edit-modal-hint" data-test="task-edit-modal-open-mode-hint">
                <KsText size="small">
                    <i18n-t keypath="block_editor.open_mode_hint" scope="global">
                        <template #settings>
                            <router-link :to="{name: 'preferences'}">{{ t("settings.label") }}</router-link>
                        </template>
                    </i18n-t>
                </KsText>
                <KsIconButton
                    class="task-edit-modal-hint-dismiss"
                    :aria-label="t('close')"
                    :tooltip="t('close')"
                    data-test="task-edit-modal-open-mode-hint-dismiss"
                    @click="dismissHint"
                >
                    <Close />
                </KsIconButton>
            </div>
        </template>
    </KsDialog>
</template>

<script setup lang="ts">
    import {computed, provide, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import {KsDialog, KsIconButton, KsText, vKsLoading} from "@kestra-io/design-system"
    import HelpCircleOutline from "vue-material-design-icons/HelpCircleOutline.vue"
    import DockWindow from "vue-material-design-icons/DockWindow.vue"
    import Close from "vue-material-design-icons/Close.vue"
    import TaskEdit from "../../flows/TaskEdit.vue"
    import PluginDocumentation from "../../plugins/PluginDocumentation.vue"
    import {usePluginsStore, type PluginComponent} from "../../../stores/plugins"
    import type {BlockSection} from "../../../utils/flowableBlockOps"
    import {storageKeys} from "../../../utils/constants"
    import {
        BLOCK_SCHEMA_PATH_INJECTION_KEY,
        EDITING_TASK_INJECTION_KEY,
        PARENT_PATH_INJECTION_KEY,
        REF_PATH_INJECTION_KEY,
    } from "../injectionKeys"

    const props = defineProps<{
        task?: Record<string, unknown>
        taskRaw?: string
        section: BlockSection
        flowId: string
        namespace: string
        editorKey: string
        parentPath: string
        refPath?: number
        blockSchemaPath: string
    }>()

    const emit = defineEmits<{
        (e: "update:task", value: string): void
        (e: "close"): void
        (e: "open-in-tabs"): void
    }>()

    const {t} = useI18n()
    const pluginsStore = usePluginsStore()

    const hintDismissed = ref(localStorage.getItem(storageKeys.TASK_EDIT_MODE_HINT_DISMISSED) === "true")

    function dismissHint() {
        hintDismissed.value = true
        localStorage.setItem(storageKeys.TASK_EDIT_MODE_HINT_DISMISSED, "true")
    }

    provide(PARENT_PATH_INJECTION_KEY, props.parentPath)
    provide(REF_PATH_INJECTION_KEY, props.refPath)
    provide(EDITING_TASK_INJECTION_KEY, true)
    provide(BLOCK_SCHEMA_PATH_INJECTION_KEY, computed(() => props.blockSchemaPath))

    const title = computed(() => {
        const id = (props.task?.id as string) ?? ""
        const type = (props.task?.type as string) ?? ""
        return type ? `${id} · ${type}` : id
    })

    const docsOpen = ref(false)
    const docsLoading = ref(false)
    const docsPlugin = ref<PluginComponent>()

    async function toggleDocs() {
        docsOpen.value = !docsOpen.value
        const cls = props.task?.type as string | undefined
        if (!docsOpen.value || !cls || docsPlugin.value?.cls === cls) return

        docsLoading.value = true
        try {
            const data = await pluginsStore.load({cls, commit: false})
            docsPlugin.value = {...data, cls}
        } finally {
            docsLoading.value = false
        }
    }
</script>

<style lang="scss" scoped>
    .task-edit-modal-header {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
        width: 100%;
    }

    .task-edit-modal-title {
        flex: 1;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    :global(.task-edit-modal-header .task-edit-modal-header-action) {
        flex-shrink: 0;
        margin-left: 0;
    }

    .task-edit-modal-body {
        display: flex;
        height: 82vh;
        min-height: 0;
        gap: var(--ks-spacing-4);
    }

    .task-edit-modal-form {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        min-height: 0;
    }

    .task-edit-modal-docs {
        flex: 0 0 400px;
        min-height: 0;
        overflow-y: auto;
        border-left: 1px solid var(--ks-border-default);
        padding-left: var(--ks-spacing-4);
    }

    .task-edit-modal-hint {
        display: flex;
        align-items: center;
        justify-content: flex-start;
        gap: var(--ks-spacing-2);
        text-align: left;

        a {
            color: var(--ks-text-link);
        }
    }

    .task-edit-modal-hint-dismiss {
        flex-shrink: 0;
    }
</style>
