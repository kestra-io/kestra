<template>
    <Layout
        :title="t(`demos.namespace.${props.tab}.title`)"
        :image="{source: sourceImg, alt: t(`demos.namespace.${props.tab}.title`)}"
        :video="videoSource"
    >
        <template #message>
            {{ $t(`demos.namespace.${props.tab}.message`) }}
        </template>
    </Layout>
</template>

<script setup lang="ts">
    import {useI18n} from "vue-i18n";
    import {computed} from "vue";
    import Layout from "./Layout.vue";
    import {useStore} from "vuex";
    import sourceImg from "../../assets/demo/namespace.png";

    const {t} = useI18n();
    const store = useStore();

    store.commit("doc/setDocId", "namespace.management")

    const props = defineProps<{
        tab: string;
    }>();

    const videos = {
        edit: "https://youtu.be/As4y2oliD_8",
        secrets: "https://youtu.be/u0yuOYG-qMI",
        variables: "https://youtu.be/1iSam2aftKo",
        "plugin-defaults": "https://youtu.be/9zQTUeL0KMc",
        history: "https://youtu.be/lpHl52Rlvr0",
        "audit-logs": "https://youtu.be/Qz24gBPGZHs",
    };

    const videoSource = computed(() => ({
        source: videos[props.tab as keyof typeof videos] || "",
    }));
</script>