<template>
    <KsAlert
        v-if="mcpServerEnabled && !dismissed"
        type="info"
        :title="$t('ai.copilot.mcpTip.title')"
        :closable="false"
        class="copilot-mcp-tip"
        data-test="copilot-mcp-tip"
    >
        <div class="copilot-mcp-tip-body">
            <span>{{ $t("ai.copilot.mcpTip.description") }}</span>
            <div class="copilot-mcp-tip-actions">
                <KsButton
                    size="small"
                    type="primary"
                    tag="a"
                    target="_blank"
                    :href="DOCS_URL"
                    data-test="copilot-mcp-tip-docs"
                >
                    {{ $t("ai.copilot.mcpTip.docs") }}
                </KsButton>
                <KsButton size="small" data-test="copilot-mcp-tip-dismiss" @click="dismissed = true">
                    {{ $t("ai.copilot.mcpTip.dismiss") }}
                </KsButton>
            </div>
        </div>
    </KsAlert>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useStorage} from "@vueuse/core"
    import {useMiscStore} from "override/stores/misc"
    import {storageKeys} from "../../../utils/constants"

    const DOCS_URL = "https://kestra.io/docs/develop-with-ai/kestra-mcp-server?utm_source=app&utm_medium=referral&utm_campaign=ai-copilot-mcp"

    const miscStore = useMiscStore()
    const mcpServerEnabled = computed(() => miscStore.configs?.isMcpServerEnabled === true)

    const dismissed = useStorage(storageKeys.COPILOT_MCP_TIP_DISMISSED, false, undefined, {writeDefaults: false})
</script>

<style scoped>
    .copilot-mcp-tip {
        text-align: left;
    }

    .copilot-mcp-tip-body {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        width: 100%;
    }

    .copilot-mcp-tip-actions {
        display: flex;
        flex-wrap: wrap;
        gap: var(--ks-spacing-2);
    }
</style>
