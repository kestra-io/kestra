<template>
    <KsButton
        size="small"
        type="primary"
        :icon="EyeOutline"
        @click="selectedPreview = value; isPreviewOpen = true"
        :disabled="isZipFile"
    >
        {{ $t("preview.label") }}
    </KsButton>
    <KsDrawer
        v-if="selectedPreview === value"
        v-model="isPreviewOpen"
    >
        <template #header>
            {{ $t("preview.label") }}
        </template>
        <template #default>
            <FilePreview 
                :path="value"
                :executionId="executionId"
            />
        </template>
    </KsDrawer>
</template>

<script setup lang="ts">
    import {ref, computed} from "vue"
    import EyeOutline from "vue-material-design-icons/EyeOutline.vue"

    const props = defineProps({
        value: {
            type: String,
            required: true,
        },
        executionId: {
            type: String,
            required: false,
            default: undefined,
        },
    })

    const isPreviewOpen = ref(false)
    const selectedPreview = ref<string | null>(null)

    const isZipFile = computed(() => {
        return props.value?.toLowerCase().endsWith(".zip")
    })


</script>
<style scoped lang="scss">
    :deep(.editor-container) {
        min-height: 65px !important;
    }
</style>