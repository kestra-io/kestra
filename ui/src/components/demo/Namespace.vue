<template>
    <Empty
        type="namespace"
        demoCta
        :title="$t(`demos.namespace.${props.tab}.title`)"
        :video="videoSource"
        learnMore="https://kestra.io/docs/enterprise/governance/namespace-management"
    >
        <template #description>
            {{ $t(`demos.namespace.${props.tab}.message`) }}
        </template>
    </Empty>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import Empty from "../layout/empty/Empty.vue"
    import {useDocStore} from "../../stores/doc"

    const docStore = useDocStore()

    docStore.docId = "namespace.management"

    const props = defineProps<{
        tab: string;
    }>()

    const videos = {
        edit: "https://www.youtube.com/embed/As4y2oliD_8",
        secrets: "https://www.youtube.com/embed/u0yuOYG-qMI",
        assets: "https://www.youtube.com/embed/XhICXP_GXic",
        variables: "https://www.youtube.com/embed/1iSam2aftKo",
        "plugin-defaults": "https://www.youtube.com/embed/9zQTUeL0KMc",
        history: "https://www.youtube.com/embed/lpHl52Rlvr0",
        "audit-logs": "https://www.youtube.com/embed/Qz24gBPGZHs",
    }

    const videoSource = computed(
        () => videos[props.tab as keyof typeof videos] ?? "",
    )
</script>