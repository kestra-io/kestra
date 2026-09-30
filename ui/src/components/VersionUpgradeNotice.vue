<template>
    <KsAlert
        v-if="visible"
        type="info"
        closable
        class="banner"
        @close="dismiss"
    >
        <template #title>
            <span class="banner-content">
                <span class="banner-message">
                    {{ $t("versionUpgradeNotice.message", {version: notice!.to}) }}
                </span>
                <KsButton
                    v-if="migrationGuideUrl"
                    type="primary"
                    size="small"
                    tag="a"
                    :href="migrationGuideUrl"
                    target="_blank"
                    rel="noopener noreferrer"
                >
                    {{ $t("versionUpgradeNotice.cta") }}
                </KsButton>
            </span>
        </template>
    </KsAlert>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {KsAlert, KsButton} from "@kestra-io/design-system"
    import {useVersionUpgradeNotice} from "../composables/useVersionUpgradeNotice"

    const {notice, visible, dismiss} = useVersionUpgradeNotice()

    // Guides are published per minor release, always with a .0 patch: /docs/migration-guide/v2.0.0.
    // A version that does not carry both a major and a minor (or carries something unparsable) names
    // no guide, and the button is dropped rather than pointing at a 404.
    const migrationGuideUrl = computed(() => {
        const [major, minor] = (notice.value?.to ?? "").split("-")[0].split(".")
        const isNumber = (part?: string) => part !== undefined && /^\d+$/.test(part)

        return isNumber(major) && isNumber(minor)
            ? `https://kestra.io/docs/migration-guide/v${major}.${minor}.0`
            : undefined
    })
</script>

<style lang="scss" scoped>
    // Spans the viewport above the app shell, so the card treatment of a standard alert is dropped.
    // Matches the announcement, kill-switch and maintenance banners in the EE shell.
    .banner {
        border-left: none;
        border-right: none;
        border-top: none;
        border-radius: 0;
        flex-shrink: 0;
    }

    .banner-content {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-4);
        flex-wrap: wrap;
    }

    // Darker than the alert's own info tint, which was too low-contrast to read at a glance.
    .banner-message {
        color: var(--ks-text-primary);
        font-size: var(--ks-font-size-base);
    }
</style>
