<template>
    <div class="mcp-connect">
        <section class="section">
            <h3 class="heading">
                <LinkVariant class="heading-icon" />
                {{ t("mcp.connect_tab.server_url") }}
            </h3>
            <KsInput
                :modelValue="serverUrl"
                readonly
                class="url"
            >
                <template #suffix>
                    <KsTooltip
                        trigger="click"
                        :content="t('copied')"
                        :autoClose="2000"
                        placement="top"
                    >
                        <ContentCopy
                            class="copy"
                            :aria-label="t('copy_to_clipboard')"
                            @click="copyUrl"
                        />
                    </KsTooltip>
                </template>
            </KsInput>
        </section>
    </div>

    <div class="mcp-connect">
        <h3 class="heading">
            <Connection class="heading-icon" />
            {{ t("mcp.client_setup") }}
        </h3>

        <KsTabs
            v-model="selectedClient"
            type="segmented"
        >
            <KsTabPane
                v-for="opt in clientOptions"
                :key="opt.value"
                :name="opt.value"
                :label="opt.label"
            />
        </KsTabs>

        <section class="section">
            <div class="hints">
                <p
                    v-if="authHintKey"
                    class="hint"
                    v-html="t(authHintKey)"
                />
                <p class="hint">
                    {{ clientHint }}
                </p>
            </div>

            <div class="source">
                <KsIconButton
                    class="copy-btn"
                    :aria-label="copyLabel"
                    @click="copySnippet"
                >
                    <CheckIcon
                        v-if="copied"
                        class="text-success"
                    />
                    <ContentCopy v-else />
                </KsIconButton>
                <KsEditor
                    v-bind="editorBindings"
                    :modelValue="snippetCode"
                    :lang="snippetLang"
                    inline
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

            <div
                v-if="isClaudeDesktop"
                class="paths"
            >
                <p class="path-hint">
                    {{ t("mcp.connect_tab.claude_desktop_mac") }}
                </p>
                <p class="path-hint">
                    {{ t("mcp.connect_tab.claude_desktop_win") }}
                </p>
            </div>
        </section>
    </div>
</template>

<script lang="ts" setup>
    import {computed, ref} from "vue"
    import {useRoute} from "vue-router"
    import {useI18n} from "vue-i18n"

    import {useMcpStore, type McpAuthType, type McpServer} from "../../../../stores/mcp"
    import {useMiscStore} from "override/stores/misc"

    import {useEditorBindings} from "../../../../composables/useEditorBindings"

    import {KsEditor} from "@kestra-io/design-system"
    import LinkVariant from "vue-material-design-icons/LinkVariant.vue"
    import Connection from "vue-material-design-icons/Connection.vue"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import CheckIcon from "vue-material-design-icons/Check.vue"

    import {baseUrl} from "override/utils/route"
    import {copy} from "../../../../utils/utils"

    defineOptions({inheritAttrs: false})

    type ClientId = "claude_desktop" | "claude_code" | "cursor" | "codex";

    const OAUTH_CLIENT_ID_PLACEHOLDER = "<client-id>"
    const OAUTH_CALLBACK_PORT = "7777"
    const COPY_FEEDBACK_MS = 2000

    const {t} = useI18n({useScope: "global"})
    const route = useRoute()
    const mcpStore = useMcpStore()
    const miscStore = useMiscStore()
    const editorBindings = useEditorBindings()

    const selectedClient = ref<ClientId>("claude_desktop")
    const copied = ref(false)

    const tenant = computed(() => (route.params.tenant as string) ?? "main")
    const serverId = computed(() => route.params.id as string)

    const serverUrl = computed(() => {
        const configured = (miscStore.configs?.url as string | undefined)
            ?.replace(/\/$/, "")
        const path = `${baseUrl}/api/v1/${tenant.value}/mcp/${serverId.value}`
        const base = configured || window.location.origin
        return new URL(path, base).toString()
    })

    const authType = computed<McpAuthType>(
        () => mcpStore.server?.authType ?? "BASIC",
    )
    const serverType = computed<McpServer["serverType"]>(
        () => mcpStore.server?.serverType ?? "PRIVATE",
    )
    const isPublic = computed(() => serverType.value === "PUBLIC")
    const isClaudeDesktop = computed(() => selectedClient.value === "claude_desktop")

    const clientOptions = computed(() => [
        {label: t("mcp.connect_tab.claude_desktop"), value: "claude_desktop"},
        {label: t("mcp.connect_tab.claude_code"), value: "claude_code"},
        {label: t("mcp.connect_tab.cursor"), value: "cursor"},
        {label: t("mcp.connect_tab.codex"), value: "codex"},
    ])

    /**
     * Authorization header for private servers. OAUTH returns null because PKCE
     * is handled by mcp-remote / Cursor / Codex via the PRM challenge.
     */
    const authHeaderValue = computed<string | null>(() => {
        if (isPublic.value) {
            return null
        }
        switch (authType.value) {
        case "API_TOKEN":
            return "Bearer ${KESTRA_TOKEN}"
        case "BASIC":
            return "Basic ${KESTRA_BASIC_AUTH}"
        case "OAUTH":
            return null
        }
        return null
    })

    const claudeDesktopConfig = computed(() => {
        const args: string[] = ["-y", "mcp-remote", serverUrl.value]
        if (authHeaderValue.value) {
            args.push("--header", `Authorization: ${authHeaderValue.value}`)
        }
        return JSON.stringify({
            mcpServers: {
                [serverId.value]: {command: "npx", args},
            },
        }, null, 2)
    })

    const claudeCodeCommand = computed(() => {
        const lines: string[] = [
            `claude mcp add ${serverId.value} ${serverUrl.value}`,
            "--transport http",
        ]
        if (!isPublic.value && authType.value === "OAUTH") {
            lines.push(`--client-id ${OAUTH_CLIENT_ID_PLACEHOLDER}`)
            lines.push(`--callback-port ${OAUTH_CALLBACK_PORT}`)
        } else if (authHeaderValue.value) {
            lines.push(`--header "Authorization: ${authHeaderValue.value}"`)
        }
        return lines.join(" \\\n  ")
    })

    const cursorConfig = computed(() => {
        const config: Record<string, unknown> = {url: serverUrl.value}
        if (authHeaderValue.value) {
            config.headers = {Authorization: authHeaderValue.value}
        }
        return JSON.stringify({
            mcpServers: {
                [serverId.value]: config,
            },
        }, null, 2)
    })

    const codexConfig = computed(() => {
        const lines: string[] = [
            "[[mcp_servers]]",
            `name = "${serverId.value}"`,
            `url = "${serverUrl.value}"`,
        ]
        if (authHeaderValue.value) {
            lines.push("[mcp_servers.headers]")
            lines.push(`Authorization = "${authHeaderValue.value}"`)
        }
        return lines.join("\n")
    })

    const snippetCode = computed<string>(() => {
        switch (selectedClient.value) {
        case "claude_desktop": return claudeDesktopConfig.value
        case "claude_code": return claudeCodeCommand.value
        case "cursor": return cursorConfig.value
        case "codex": return codexConfig.value
        default: return ""
        }
    })

    /** Monaco has no TOML grammar, so codex falls back to INI and bash to shell. */
    const snippetLang = computed<string>(() => {
        switch (selectedClient.value) {
        case "claude_code": return "shell"
        case "codex": return "ini"
        default: return "json"
        }
    })

    const clientHint = computed(() => t(`mcp.connect_tab.${selectedClient.value}_hint`))
    const copyLabel = computed(() => (copied.value ? t("copied") : t("copy")))

    /**
     * For OAUTH, only Claude Code's command exposes a client-id placeholder;
     * other clients get the generic "browser will open" hint instead.
     */
    const authHintKey = computed<string | null>(() => {
        if (isPublic.value) {
            return null
        }
        if (authType.value === "BASIC") {
            return "mcp.connect_tab.auth_basic_hint"
        }
        if (authType.value === "API_TOKEN") {
            return "mcp.connect_tab.auth_api_token_hint"
        }
        if (selectedClient.value === "claude_code") {
            return "mcp.connect_tab.auth_oauth_client_id_hint"
        }
        return "mcp.connect_tab.auth_oauth_browser_hint"
    })

    function copyUrl() {
        copy(serverUrl.value)
    }

    async function copySnippet() {
        await copy(snippetCode.value)
        copied.value = true
        setTimeout(() => (copied.value = false), COPY_FEEDBACK_MS)
    }
</script>

<style lang="scss" scoped>
    .mcp-connect {
        max-width: 653px;
        border: 1px solid var(--ks-border-default);
        border-radius: 8px;
        box-shadow: 0px 2px 8px 0px var(--ks-shadow-surface);
        background: var(--ks-bg-surface);
        padding: var(--ks-spacing-4);
        margin-block-start: var(--ks-spacing-7);
        margin-inline: auto;
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
    }

    .section {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;

        .hints {
            margin: 0.5rem 0;
        }
    }

    .heading {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        font-size: var(--ks-font-size-md);
        font-weight: var(--ks-font-weight-semibold);
        color: var(--ks-text-primary);
    }

    .heading-icon {
        color: var(--ks-icon-muted);
    }

    .hint {
        margin: 0;
        font-size: var(--ks-font-size-sm);
        font-weight: var(--ks-font-weight-regular);
        line-height: 1.125rem;
        color: var(--ks-text-primary);
    }

    .path-hint {
        margin: 0;
        font-size: var(--ks-font-size-sm);
        font-weight: var(--ks-font-weight-semibold);
        line-height: 1.125rem;
        color: var(--ks-text-secondary);
        font-family: var(--ks-font-family-mono);
    }

    .source {
        position: relative;
    }

    .copy-btn {
        position: absolute;
        top: var(--ks-spacing-2);
        right: var(--ks-spacing-2);
        z-index: 2;
    }

    .url {
        :deep(.kel-input__wrapper) {
            height: 42px;
        }

        :deep(.kel-input__inner) {
            font-family: var(--ks-font-family-mono);
            font-size: var(--ks-font-size-sm);
        }
    }

    .copy {
        display: inline-flex;
        color: var(--ks-icon-muted);
        cursor: pointer;

        &:hover {
            color: var(--ks-text-primary);
        }
    }
</style>