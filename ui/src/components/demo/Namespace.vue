<template>
    <Empty
        type="namespace"
        demoCta
        :title="$t(`demos.namespace.${props.tab}.title`)"
    >
        <template #description>
            {{ $t(`demos.namespace.${props.tab}.message`) }}
        </template>
    </Empty>
</template>

<script setup lang="ts">
    import Empty from "../layout/empty/Empty.vue"
    import {useDocStore} from "../../stores/doc"

    const docStore = useDocStore()

    docStore.docId = "namespace.management"

    const props = defineProps<{
        tab: string;
    }>()
</script>
