<template>
    <div class="plugin-versioning-upsell">
        <KsCard shadow="never" data-test="plugin-versioning-upsell">
            <div class="plugin-versioning-upsell-content">
                <span class="plugin-versioning-upsell-art">
                    <LockOutline :size="20" />
                </span>
                <div class="plugin-versioning-upsell-body">
                    <h2 class="plugin-versioning-upsell-title">
                        {{ $t("pluginPage.management.upsellTitle") }}
                    </h2>
                    <KsText size="small" class="plugin-versioning-upsell-description">
                        {{ $t("pluginPage.management.upsellDescription") }}
                    </KsText>
                    <div class="plugin-versioning-upsell-actions">
                        <KsButton type="primary" size="small" tag="a" target="_blank" :href="CONTACT_SALES_URL">
                            {{ $t("pluginPage.management.talkToUs") }}
                        </KsButton>
                        <KsButton size="small" tag="a" target="_blank" :href="DOCS_URL">
                            {{ $t("pluginPage.management.readTheDocs") }}
                            <OpenInNew :size="14" class="plugin-versioning-upsell-external" />
                        </KsButton>
                    </div>
                </div>
            </div>
        </KsCard>
    </div>
</template>

<script setup lang="ts">
    import LockOutline from "vue-material-design-icons/LockOutline.vue"
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"
    import {links} from "../layout/empty/links"

    const DOCS_URL = links.versionPlugin
    const CONTACT_SALES_URL = "https://kestra.io/demo?utm_source=app&utm_medium=referral&utm_campaign=demo-versionPlugin"
</script>

<style scoped>
    .plugin-versioning-upsell {
        padding: var(--ks-spacing-6) var(--ks-spacing-6) 0;
    }

    .plugin-versioning-upsell-external {
        margin-left: var(--ks-spacing-1);
    }

    .plugin-versioning-upsell-content {
        display: flex;
        align-items: flex-start;
        gap: var(--ks-spacing-3);
    }

    .plugin-versioning-upsell-art {
        display: inline-flex;
        flex: 0 0 auto;
        align-items: center;
        justify-content: center;
        width: 2rem;
        height: 2rem;
        border-radius: var(--ks-radius-base);
        background: var(--ks-bg-tag);
        color: var(--ks-icon-muted);
    }

    .plugin-versioning-upsell-body {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
        min-width: 0;
    }

    .plugin-versioning-upsell-title {
        margin: 0;
        font-size: var(--ks-font-size-sm);
        font-weight: var(--ks-font-weight-semibold);
        color: var(--ks-text-primary);
    }

    .plugin-versioning-upsell-description {
        max-width: 60rem;
        color: var(--ks-text-secondary);
    }

    .plugin-versioning-upsell-actions {
        display: flex;
        margin-top: var(--ks-spacing-1);
        flex-wrap: wrap;
        gap: var(--ks-spacing-2);
    }
</style>
