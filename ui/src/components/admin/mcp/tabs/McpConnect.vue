<template>
    <div class="mcp-connect">
        <section class="mcp-connect__section">
            <h3 class="mcp-connect__heading">
                {{ t("mcp.connect_tab.server_url") }}
            </h3>
            <KsMarkdown class="mcp-connect__code" :content="serverUrlMarkdown" />
        </section>

        <section class="mcp-connect__section">
            <KsSegmented
                :modelValue="selectedClient"
                :options="clientOptions"
                @change="onClientChange"
            />
        </section>

        <section class="mcp-connect__section">
            <header class="mcp-connect__client-header">
                <img
                    class="mcp-connect__brand-icon"
                    :src="brandLogo(selectedClient)"
                    :alt="t(`mcp.connect_tab.${selectedClient}`)"
                >
                <h3 class="mcp-connect__heading">
                    {{ t(`mcp.connect_tab.${selectedClient}`) }}
                </h3>
            </header>

            <p v-if="authHintKey" class="mcp-connect__hint" v-html="t(authHintKey)" />

            <p class="mcp-connect__hint">
                {{ t(`mcp.connect_tab.${selectedClient}_hint`) }}
            </p>

            <KsMarkdown class="mcp-connect__code" :content="snippetMarkdown" />

            <template v-if="selectedClient === 'claude_desktop'">
                <p class="mcp-connect__path-hint">
                    {{ t("mcp.connect_tab.claude_desktop_mac") }}
                </p>
                <p class="mcp-connect__path-hint">
                    {{ t("mcp.connect_tab.claude_desktop_win") }}
                </p>
            </template>
        </section>
    </div>
</template>

<script lang="ts" setup>
    import {computed, ref} from "vue"
    import {useRoute} from "vue-router"
    import {useI18n} from "vue-i18n"
    import {KsMarkdown, KsSegmented} from "@kestra-io/design-system"
    import {useMcpStore} from "../../../../stores/mcp"
    import {useMiscStore} from "override/stores/misc"
    import {baseUrl} from "override/utils/route"
    import claudeDesktopLogo from "../../../../assets/icons/mcp-clients/claude-desktop.svg"
    import claudeCodeLogo from "../../../../assets/icons/mcp-clients/claude-code.svg"
    import cursorLogo from "../../../../assets/icons/mcp-clients/cursor.svg"
    import codexLogo from "../../../../assets/icons/mcp-clients/codex.svg"

    type ClientId = "claude_desktop" | "claude_code" | "cursor" | "codex"
    type AuthType = "BASIC" | "API_TOKEN" | "OAUTH"
    type ServerType = "PRIVATE" | "PUBLIC"

    const {t} = useI18n({useScope: "global"})
    const route = useRoute()
    const mcpStore = useMcpStore()
    const miscStore = useMiscStore()

    const tenant = computed(() => (route.params.tenant as string) ?? "main")
    const serverId = computed(() => route.params.id as string)

    const serverUrl = computed(() => {
        const configured = (miscStore.configs?.url as string | undefined)?.replace(/\/$/, "")
        const path = `${baseUrl}/api/v1/${tenant.value}/mcp/${serverId.value}`
        const base = configured || window.location.origin
        return new URL(path, base).toString()
    })

    // Falls back to BASIC / PRIVATE while the server is loading or if absent.
    const authType = computed<AuthType>(() => mcpStore.server?.authType ?? "BASIC")
    const serverType = computed<ServerType>(() => (mcpStore.server?.serverType as ServerType | undefined) ?? "PRIVATE")
    const isPublic = computed(() => serverType.value === "PUBLIC")
    const oauthClientIdPlaceholder = "<client-id>"
    const oauthCallbackPort = "7777"

    const selectedClient = ref<ClientId>("claude_desktop")

    function onClientChange(value: string | number | boolean) {
        selectedClient.value = value as ClientId
    }

    const clientOptions = computed(() => [
        {label: t("mcp.connect_tab.claude_desktop"), value: "claude_desktop"},
        {label: t("mcp.connect_tab.claude_code"), value: "claude_code"},
        {label: t("mcp.connect_tab.cursor"), value: "cursor"},
        {label: t("mcp.connect_tab.codex"), value: "codex"},
    ])

    const authHeaderValue = computed<string | null>(() => {
        if (isPublic.value) return null
        switch (authType.value) {
        case "API_TOKEN":
            return "Bearer ${KESTRA_TOKEN}"
        case "BASIC":
            return "Basic ${KESTRA_BASIC_AUTH}"
        case "OAUTH": // PKCE handled by mcp-remote / Cursor / Codex via PRM challenge
            return null
        }
        return null
    })

    // ── Per-client snippet builders ──────────────────────────────────────────
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
        const lines: string[] = [`claude mcp add ${serverId.value} ${serverUrl.value}`, "--transport http"]
        if (!isPublic.value && authType.value === "OAUTH") {
            lines.push(`--client-id ${oauthClientIdPlaceholder}`)
            lines.push(`--callback-port ${oauthCallbackPort}`)
        } else if (authHeaderValue.value) {
            lines.push(`--header "Authorization: ${authHeaderValue.value}"`)
        }
        return lines.join(" \\\n  ")
    })

    // Cursor (~/.cursor/mcp.json) — JSON
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

    // Codex (~/.codex/config.toml) — TOML
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

    function fence(lang: string, code: string) {
        return `\`\`\`${lang}\n${code}\n\`\`\``
    }

    const serverUrlMarkdown = computed(() => fence("text", serverUrl.value))

    const snippetMarkdown = computed<string>(() => {
        switch (selectedClient.value) {
        case "claude_desktop": return fence("json", claudeDesktopConfig.value)
        case "claude_code": return fence("bash", claudeCodeCommand.value)
        case "cursor": return fence("json", cursorConfig.value)
        case "codex": return fence("toml", codexConfig.value)
        default: return ""
        }
    })

    // ── Auth-specific hint shown under the snippet ───────────────────────────
    const authHintKey = computed<string | null>(() => {
        if (isPublic.value) return null
        if (authType.value === "BASIC") {
            return "mcp.connect_tab.auth_basic_hint"
        }
        if (authType.value === "API_TOKEN") {
            return "mcp.connect_tab.auth_api_token_hint"
        }
        // OAUTH: only Claude Code's command exposes a client-id placeholder; other clients
        // get the generic "your browser will open" hint instead.
        if (selectedClient.value === "claude_code") {
            return "mcp.connect_tab.auth_oauth_client_id_hint"
        }
        return "mcp.connect_tab.auth_oauth_browser_hint"
    })

    function brandLogo(client: ClientId): string {
        switch (client) {
        case "claude_desktop": return claudeDesktopLogo
        case "claude_code": return claudeCodeLogo
        case "cursor": return cursorLogo
        case "codex": return codexLogo
        }
    }
</script>

<style lang="scss" scoped>
    .mcp-connect {
        display: flex;
        flex-direction: column;
        gap: 1.5rem;
        padding: 1.5rem;

        &__section {
            display: flex;
            flex-direction: column;
            gap: 0.5rem;
        }

        &__heading {
            font-size: 0.9375rem;
            font-weight: 600;
            color: var(--ks-text-primary);
            margin: 0;
        }

        &__client-header {
            display: flex;
            align-items: center;
            gap: 0.5rem;
        }

        &__hint {
            font-size: 0.875rem;
            color: var(--ks-text-secondary);
            margin: 0;
        }

        &__path-hint {
            font-size: 0.8125rem;
            color: var(--ks-text-secondary);
            margin: 0;
            font-family: var(--ks-font-family-mono);
        }

        &__code {
            width: 100%;
        }

        &__brand-icon {
            display: inline-block;
            width: 1.5rem;
            height: 1.5rem;
        }

    }
</style>
