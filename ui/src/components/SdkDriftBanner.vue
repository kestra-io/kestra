<template>
    <KsAlert
        v-if="visible"
        type="warning"
        :title="$t('sdkDrift.title')"
        closable
        class="sdk-drift-banner"
        @close="dismiss"
    >
        {{ $t("sdkDrift.message", {committed: detail?.committedHash, live: detail?.liveHash}) }}
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
