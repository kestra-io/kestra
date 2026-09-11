<template>
    <aside v-if="visible" class="version-upgrade-notice">
        <div class="notice-header">
            <InformationOutline :size="16" />
            <KsIconButton size="xs" :tooltip="$t('close')" placement="right" @click="dismiss">
                <Close :size="14" />
            </KsIconButton>
        </div>
        <p class="notice-message">
            {{ $t("versionUpgradeNotice.message", {version: notice!.to}) }}
        </p>
        <KsButton
            class="notice-cta"
            type="primary"
            size="small"
            tag="a"
            :href="migrationGuideUrl"
            target="_blank"
            rel="noopener noreferrer"
        >
            {{ $t("versionUpgradeNotice.cta") }}
        </KsButton>
    </aside>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {KsButton, KsIconButton} from "@kestra-io/design-system"
    import Close from "vue-material-design-icons/Close.vue"
    import InformationOutline from "vue-material-design-icons/InformationOutline.vue"
    import {useVersionUpgradeNotice} from "../composables/useVersionUpgradeNotice"

    const {notice, visible, dismiss} = useVersionUpgradeNotice()

    // Guides are published per minor release, always with a .0 patch: /docs/migration-guide/v2.0.0.
    const migrationGuideUrl = computed(() => {
        const [major, minor] = (notice.value?.to ?? "").split(".")
        return `https://kestra.io/docs/migration-guide/v${major}.${minor}.0`
    })
</script>

<style lang="scss" scoped>
    .version-upgrade-notice {
        margin: 0 var(--ks-spacing-4) var(--ks-spacing-3);
        padding: var(--ks-spacing-3);
        border: var(--ks-border-width-thin) solid var(--ks-border-info);
        border-radius: var(--ks-radius-base);
        background: var(--ks-bg-info);
    }

    .notice-header {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        color: var(--ks-icon-info);
    }

    .notice-message {
        margin: var(--ks-spacing-1) 0 var(--ks-spacing-3);
        color: var(--ks-text-info);
        font-size: var(--ks-font-size-xs);
        line-height: var(--ks-line-height-base);
    }

    .notice-cta {
        width: 100%;
    }
</style>
