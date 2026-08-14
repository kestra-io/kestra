<template>
    <KsAlert
        v-if="visible"
        type="info"
        :title="$t('pwa.install_title')"
        closable
        class="pwa-install-prompt"
        @close="dismiss"
    >
        <p class="pwa-install-description">
            {{ $t("pwa.install_description") }}
        </p>
        <div class="pwa-install-actions">
            <KsButton type="primary" size="small" @click="install">
                {{ $t("pwa.install") }}
            </KsButton>
            <KsButton text size="small" @click="dismiss">
                {{ $t("pwa.dismiss") }}
            </KsButton>
        </div>
    </KsAlert>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {usePwaInstall} from "../composables/usePwaInstall"

    const {canInstall, promptInstall, dismiss, dismissed} = usePwaInstall()

    const visible = computed(() => canInstall.value && !dismissed.value)

    async function install() {
        await promptInstall()
    }
</script>

<style lang="scss" scoped>
    .pwa-install-prompt {
        position: fixed;
        bottom: var(--ks-spacing-5);
        right: var(--ks-spacing-5);
        width: 22rem;
        max-width: calc(100vw - var(--ks-spacing-6));
        z-index: 1000;
        box-shadow: var(--ks-shadow-lg);
        &:before {
            content: "";
            z-index: -1;
            position: absolute;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background-color: var(--ks-bg-base);
            border-radius: var(--ks-radius-lg);
            opacity: 1;
        }
    }

    .pwa-install-description {
        margin: 0 0 var(--ks-spacing-3);
    }

    .pwa-install-actions {
        display: flex;
        gap: var(--ks-spacing-2);
    }
</style>
