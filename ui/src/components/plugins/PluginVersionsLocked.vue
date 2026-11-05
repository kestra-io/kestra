<template>
    <div class="plugin-versions-locked" data-test="plugin-versions-locked">
        <h6 class="plugin-versions-locked-title">
            <LockOutline :size="16" />
            {{ $t("pluginPage.management.lockedTitle") }}
        </h6>
        <KsText size="small" class="plugin-versions-locked-description">
            {{ $t("pluginPage.management.lockedDescription") }}
        </KsText>
        <div class="plugin-versions-locked-actions">
            <KsButton size="small" tag="a" target="_blank" :href="DOCS_URL">
                {{ $t("pluginPage.management.learnMore") }}
            </KsButton>
            <KsButton size="small" type="primary" tag="a" target="_blank" :href="CONTACT_SALES_URL">
                {{ $t("demos.contact_sales") }}
            </KsButton>
        </div>
    </div>
</template>

<script setup lang="ts">
    import LockOutline from "vue-material-design-icons/LockOutline.vue"
    import {demoLink, links} from "../layout/empty/links"

    defineOptions({inheritAttrs: false})

    const DOCS_URL = links.versionPlugin
    const CONTACT_SALES_URL = demoLink("versionPlugin")
</script>

<style scoped>
    .plugin-versions-locked {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
    }

    .plugin-versions-locked-title {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        margin: 0;
        font-size: var(--ks-font-size-xs);
        font-weight: 600;
        color: var(--ks-text-primary);
    }

    .plugin-versions-locked-description {
        color: var(--ks-text-secondary);
    }

    .plugin-versions-locked-actions {
        display: flex;
        gap: var(--ks-spacing-2);
    }
</style>
