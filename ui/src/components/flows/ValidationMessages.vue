<template>
    <div v-if="messages.length" class="validation-messages">
        <KsText v-for="message in messages" :key="message" type="danger" size="small">
            {{ message }}
        </KsText>
    </div>
</template>

<script setup lang="ts">
    defineProps<{messages: string[]}>()
</script>

<style scoped lang="scss">
    .validation-messages {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
        text-align: right;
    }
</style>
