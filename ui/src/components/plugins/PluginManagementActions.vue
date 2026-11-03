<template>
    <div class="plugin-management">
        <ul class="plugin-management-actions">
            <li>
                <KsTooltip :content="$t('pluginPage.management.enterpriseOnly')">
                    <span data-test="plugin-upload-locked">
                        <KsButton :icon="LockOutline" size="small" disabled>
                            {{ $t("pluginPage.management.upload") }}
                        </KsButton>
                    </span>
                </KsTooltip>
            </li>
            <li>
                <KsTooltip :content="$t('pluginPage.management.enterpriseOnly')">
                    <span data-test="plugin-add-locked">
                        <KsButton :icon="LockOutline" type="primary" size="small" disabled>
                            {{ $t("pluginPage.management.addPlugin") }}
                        </KsButton>
                    </span>
                </KsTooltip>
            </li>
        </ul>
        <KsText size="small" class="plugin-management-note" data-test="plugin-management-note">
            <LockOutline :size="14" />
            {{ $t("pluginPage.management.gateNote") }}
        </KsText>
    </div>
</template>

<script setup lang="ts">
    import LockOutline from "vue-material-design-icons/LockOutline.vue"
</script>

<style scoped>
    .plugin-management {
        display: flex;
        flex-direction: column;
        align-items: flex-end;
        gap: var(--ks-spacing-1);
    }

    .plugin-management-actions {
        display: flex;
        gap: var(--ks-spacing-2);
        margin: 0;
        padding: 0;
        list-style: none;
    }

    .plugin-management-note {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        color: var(--ks-text-secondary);
    }
</style>
