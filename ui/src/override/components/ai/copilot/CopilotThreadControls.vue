<template>
    <!--
        OSS no-op: thread management (Recents list / switch / rename / delete) is an EE-only
        feature — the Enterprise build shadows this via its own `override/` with the real controls.
        OSS runs a single implicit session, so it shows no Recents surface.
    -->
</template>

<script setup lang="ts">
    // Props/emits mirror the EE implementation so CopilotChat binds the same contract in both
    // editions; OSS simply renders nothing and never emits.
    defineProps<{activeId?: string | null}>()
    defineEmits<{(e: "select", threadId: string): void}>()
</script>
