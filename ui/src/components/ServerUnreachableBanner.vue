<template>
    <KsAlert
        v-if="unreachable"
        banner
        type="error"
        :closable="false"
        :title="$t('server unreachable')"
        :description="$t(autosave.status === 'offline' ? 'flowAutosave.savedLocally.banner' : 'server unreachable description')"
        data-test="server-unreachable-banner"
    />
</template>

<script setup lang="ts">
    import {useServerReachability} from "../composables/useServerReachability"
    import {useFlowAutosaveStore} from "../stores/flowAutosave"

    const {unreachable} = useServerReachability()
    const autosave = useFlowAutosaveStore()
</script>
