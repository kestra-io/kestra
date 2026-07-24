<template>
    <!-- English only, no i18n keys: this banner only ever renders in a local dev build (see the
         defineAsyncComponent guard in App.vue), never in front of a real end user. -->
    <KsAlert
        v-if="visible"
        type="warning"
        :title="message"
        closable
        class="sdk-drift-banner"
        @close="dismiss"
    />
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useSdkDriftBanner} from "../composables/useSdkDriftBanner"

    const {detail, dismissed, dismiss} = useSdkDriftBanner()

    const visible = computed(() => detail.value !== null && !dismissed.value)

    // Single-line title (no description slot) keeps this compact — see the height cap below.
    const message = computed(() =>
        "SDK out of date: the generated SDK looks out of date with this backend's OpenAPI spec " +
        `(SDK ${detail.value?.committedHash} ≠ backend ${detail.value?.liveHash}). ` +
        "Run `npm run generate:sdk` from ui/ and refresh.",
    )
</script>

<style lang="scss" scoped>
    .sdk-drift-banner {
        border-left: none;
        border-right: none;
        border-top: none;
        border-radius: 0;
        flex-shrink: 0;
        // Capped at 70px per design review — no --ks-spacing-* token lands exactly there, so this
        // falls back to a raw rem value rather than a hardcoded px per ui/AGENTS.md.
        max-height: 4.375rem;
        padding-top: var(--ks-spacing-2);
        padding-bottom: var(--ks-spacing-2);
        overflow: hidden;
    }
</style>
