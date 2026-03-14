<template>
    <ks-button class="main alternative">
        {{ label }}
    </ks-button>
</template>

<script setup lang="ts">
    defineProps({
        label: {type: String, required: true},
    });
</script>
