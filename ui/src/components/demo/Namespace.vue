<template>
    <Layout
        :title="$t(`demos.namespace.${props.tab}.title`)"
        :image="{source: sourceImg, alt: $t(`demos.namespace.${props.tab}.title`)}"
        :video="videoSource"
    >
        <template #message>
            {{ $t(`demos.namespace.${props.tab}.message`) }}
        </template>
    </Layout>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import Layout from "./Layout.vue";
    import {useDocStore} from "../../stores/doc";
    import sourceImg from "../../assets/demo/namespace.png";

    const docStore = useDocStore();

    docStore.docId = "namespace.management";

    const props = defineProps<{
        tab: string;
    }>();

    const videos = {
        edit: "https://www.youtube.com/embed/As4y2oliD_8",
        secrets: "https://www.youtube.com/embed/u0yuOYG-qMI",
        variables: "https://www.youtube.com/embed/1iSam2aftKo",
        "plugin-defaults": "https://www.youtube.com/embed/9zQTUeL0KMc",
        history: "https://www.youtube.com/embed/lpHl52Rlvr0",
        "audit-logs": "https://www.youtube.com/embed/Qz24gBPGZHs",
    };

    const videoSource = computed(() => ({
        source: videos[props.tab as keyof typeof videos] || "",
    }));
</script>