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

    // Re-provide this modal's own task identity, shadowing the canvas
    // BlockEditor instance's values (which describe the flow root, not this
    // task) for everything rendered inside - same contract a dock-tab's
    // dedicated BlockEditor instance provides for itself.
    provide(PARENT_PATH_INJECTION_KEY, props.parentPath)
    provide(REF_PATH_INJECTION_KEY, props.refPath)
    provide(EDITING_TASK_INJECTION_KEY, true)
    provide(BLOCK_SCHEMA_PATH_INJECTION_KEY, computed(() => props.blockSchemaPath))

    // Raw task id/type, not translated copy - mirrors the dock tab's own
    // label convention (useNoCodePanels.ts's getTabFromNoCodeTab).
    const title = computed(() => {
        const id = (props.task?.id as string) ?? ""
        const type = (props.task?.type as string) ?? ""
        return type ? `${id} · ${type}` : id
    })

    // Fetched read-only (commit: false) so it never touches pluginsStore.plugin/
    // editorPlugin - those are shared singletons the flow-wide Docs tab and other
    // task views also bind to; this modal's docs column must not fight them.
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
    // The dialog's native close button is a 62x62 absolutely-positioned hit
    // target that both steals clicks from neighboring header actions and
    // forces a visible gap before its X icon - so it's disabled (showClose:
    // false) and replaced by our own close KsIconButton in the action row,
    // spaced like the other icons.
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

    // KsIconButton doesn't forward the parent's scoped data-v attribute onto
    // its root (inheritAttrs:false), so a normal scoped selector never
    // matches the rendered element here - :global() emits these without that
    // requirement, matching on class name alone. margin-left resets Element
    // Plus's default adjacent-button spacing rule, which otherwise stacks with
    // our own flex gap and doubles the visible spacing between the two icons.
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
