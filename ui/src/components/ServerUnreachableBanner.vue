<template>
    <KsAlert
        v-if="unreachable"
        banner
        type="error"
        :closable="false"
        :title="$t('server unreachable')"
        :description="$t('server unreachable description')"
        data-test="server-unreachable-banner"
    />
</template>

<script setup lang="ts">
    import {useServerReachability} from "../composables/useServerReachability"

    const {unreachable} = useServerReachability()
</script>
