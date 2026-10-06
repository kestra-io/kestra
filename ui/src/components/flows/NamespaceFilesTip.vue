<template>
    <div v-if="visible" class="namespace-files-tip" data-test="namespace-files-tip">
        <KsAlert type="info" :closable="false">
            <div class="namespace-files-tip-body">
                <div class="namespace-files-tip-header">
                    <strong>{{ $t("namespace_files_tip.title") }}</strong>
                    <KsIconButton
                        :aria-label="$t('close')"
                        :tooltip="$t('close')"
                        data-test="namespace-files-tip-close"
                        @click="closed = true"
                    >
                        <Close />
                    </KsIconButton>
                </div>
                <span>{{ $t("namespace_files_tip.description") }}</span>
                <pre class="namespace-files-tip-example"><code>{{ EXAMPLE }}</code></pre>
                <div class="namespace-files-tip-actions">
                    <KsButton
                        v-if="openEditorTab"
                        size="small"
                        type="primary"
                        data-test="namespace-files-tip-open-files"
                        @click="openEditorTab('files')"
                    >
                        {{ $t("namespace_files_tip.open_files") }}
                    </KsButton>
                    <KsButton
                        size="small"
                        data-test="namespace-files-tip-dismiss"
                        @click="dismiss"
                    >
                        {{ $t("namespace_files_tip.dont_show_again") }}
                    </KsButton>
                </div>
            </div>
        </KsAlert>
    </div>
</template>

<script setup lang="ts">
    import {computed, inject, ref} from "vue"
    import {KsAlert, KsButton, KsIconButton} from "@kestra-io/design-system"
    import Close from "vue-material-design-icons/Close.vue"
    import {useFlowStore} from "../../stores/flow"
    import {storageKeys} from "../../utils/constants"
    import {hasInlinePythonScript} from "../../utils/namespaceFilesTip"
    import {OPEN_EDITOR_TAB_INJECTION_KEY} from "../no-code/injectionKeys"

    const EXAMPLE = `tasks:
  - id: python
    type: io.kestra.plugin.scripts.python.Commands
    namespaceFiles:
      enabled: true
    commands:
      - python main.py`

    const flowStore = useFlowStore()
    const openEditorTab = inject(OPEN_EDITOR_TAB_INJECTION_KEY, undefined)

    const dismissed = ref(localStorage.getItem(storageKeys.NAMESPACE_FILES_TIP_DISMISSED) === "true")
    const closed = ref(false)

    const visible = computed(() =>
        !dismissed.value && !closed.value && hasInlinePythonScript(flowStore.flowParsed),
    )

    function dismiss() {
        dismissed.value = true
        localStorage.setItem(storageKeys.NAMESPACE_FILES_TIP_DISMISSED, "true")
    }
</script>

<style lang="scss" scoped>
    .namespace-files-tip {
        padding: var(--ks-spacing-2) var(--ks-spacing-2) 0;
    }

    .namespace-files-tip-body {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        width: 100%;
    }

    .namespace-files-tip-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--ks-spacing-2);
    }

    .namespace-files-tip-example {
        margin: 0;
        padding: var(--ks-spacing-2);
        border-radius: var(--ks-radius-base);
        background: var(--ks-bg-base);
        overflow-x: auto;
        /* Without it the longest YAML line sets the alert's minimum width and a narrow editor panel scrolls sideways. */
        contain: inline-size;
    }

    .namespace-files-tip-actions {
        display: flex;
        flex-wrap: wrap;
        gap: var(--ks-spacing-2);
    }
</style>
