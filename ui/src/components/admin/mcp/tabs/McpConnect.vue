<template>
    <div class="mcp-placeholder">
        <p class="mcp-placeholder__text">
            {{ t("mcp.tab_connect_placeholder") }}
        </p>
    </div>
</template>

<script lang="ts" setup>
    import {useI18n} from "vue-i18n"

    const {t} = useI18n({useScope: "global"})
</script>

<style lang="scss" scoped>
    .mcp-placeholder {
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 3rem;

        &__text {
            font-size: 0.9375rem;
            color: var(--ks-content-secondary);
        }
    }
</style>
