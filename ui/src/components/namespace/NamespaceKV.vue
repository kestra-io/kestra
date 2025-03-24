<template>
    <KVTable :namespace="$route.params.id" :add-kv-modal-visible="addKvModalVisible" @update:add-kv-modal-visible="$emit('update:addKvModalVisible', $event)" />
</template>

<script lang="ts" setup>
    import KVTable from "../kv/KVTable.vue";

    withDefaults(defineProps<{addKvModalVisible: boolean}>(), {
        addKvModalVisible: false
    });

    defineEmits<{
        "update:addKvModalVisible": [isVisible: boolean]
    }>()
</script>