<template>
    <BlueprintDetailPage v-bind="props" @back="emit('back')" />
</template>

<script setup lang="ts">
    import BlueprintDetailPage from "../../../../components/flows/blueprints/BlueprintDetailPage.vue"
    import type {BlueprintKind, BlueprintType} from "../../../../stores/blueprints"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        blueprintId: string;
        embed?: boolean;
        system?: boolean;
        blueprintType?: BlueprintType;
        kind?: BlueprintKind;
        combinedView?: boolean;
    }>()

    const emit = defineEmits<{back: []}>()
</script>
