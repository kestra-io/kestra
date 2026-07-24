<template>
    <!-- English only, no i18n keys: this banner only ever renders in a local dev build (see the
         defineAsyncComponent guard in App.vue), never in front of a real end user. -->
    <KsAlert
        v-if="visible"
        type="warning"
        title="SDK out of date"
        closable
        class="sdk-drift-banner"
        @close="dismiss"
    >
        The generated SDK looks out of date with this backend's OpenAPI spec (SDK {{ detail?.committedHash }} ≠ backend {{ detail?.liveHash }}). Run `npm run generate:sdk` from ui/ and refresh.
    </KsAlert>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useSdkDriftBanner} from "../composables/useSdkDriftBanner"

    const {detail, dismissed, dismiss} = useSdkDriftBanner()

    const visible = computed(() => detail.value !== null && !dismissed.value)
</script>

<style lang="scss" scoped>
    .sdk-drift-banner {
        border-left: none;
        border-right: none;
        border-top: none;
        border-radius: 0;
        flex-shrink: 0;
    }
</style>
