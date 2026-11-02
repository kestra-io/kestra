<template>
    <ul class="plugin-management-actions">
        <li>
            <KsTooltip :content="$t('pluginPage.management.enterpriseOnly')">
                <span data-test="plugin-upload-locked">
                    <KsButton :icon="LockOutline" disabled>
                        {{ $t("pluginPage.management.upload") }}
                    </KsButton>
                </span>
            </KsTooltip>
        </li>
        <li>
            <KsTooltip :content="$t('pluginPage.management.enterpriseOnly')">
                <span data-test="plugin-add-locked">
                    <KsButton :icon="LockOutline" type="primary" disabled>
                        {{ $t("pluginPage.management.addPlugin") }}
                    </KsButton>
                </span>
            </KsTooltip>
        </li>
    </ul>
</template>

<script setup lang="ts">
    import LockOutline from "vue-material-design-icons/LockOutline.vue"
</script>

<style scoped>
    .plugin-management-actions {
        display: flex;
        gap: var(--ks-spacing-2);
        margin: 0;
        padding: 0;
        list-style: none;
    }
</style>
