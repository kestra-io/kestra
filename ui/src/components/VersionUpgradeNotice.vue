<template>
    <KsAlert
        v-if="visible"
        type="info"
        center
        closable
        class="banner"
        @close="dismiss"
    >
        <template #title>
            {{ $t("versionUpgradeNotice.message", {version: notice!.to}) }}
            <KsLink type="primary" :href="migrationGuideUrl" target="_blank">
                {{ $t("versionUpgradeNotice.cta") }}
            </KsLink>
        </template>
    </KsAlert>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useVersionUpgradeNotice} from "../composables/useVersionUpgradeNotice"

    const {notice, visible, dismiss} = useVersionUpgradeNotice()

    // Guides are published per minor release, always with a .0 patch: /docs/migration-guide/v2.0.0.
    const migrationGuideUrl = computed(() => {
        const [major, minor] = (notice.value?.to ?? "").split(".")
        return `https://kestra.io/docs/migration-guide/v${major}.${minor}.0`
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
</style>
