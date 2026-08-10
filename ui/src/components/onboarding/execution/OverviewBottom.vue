<template>
    <div class="overview-bottom">
        <OverviewCard
            v-for="card in cards"
            :key="card.title"
            :title="card.title"
            :description="card.description"
            :link="card.link"
            :icon="card.icon"
        />
    </div>
</template>
<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import OverviewCard from "../execution/OverviewCard.vue"
    import PlayCircleOutline from "vue-material-design-icons/PlayCircleOutline.vue"
    import RocketLaunchOutline from "vue-material-design-icons/RocketLaunchOutline.vue"
    import ViewGridOutline from "vue-material-design-icons/ViewGridOutline.vue"
    import FolderOpenOutline from "vue-material-design-icons/FolderOpenOutline.vue"

    const {t} = useI18n()

    const props = defineProps<{isNamespace?: boolean}>()

    const cards = computed(() => [
        props.isNamespace
            ? {
                title: t("execution_guide.namespaces.title"),
                description: t("execution_guide.namespaces.text"),
                link: "https://kestra.io/docs/ui/namespaces?utm_source=app&utm_medium=referral&utm_campaign=onboarding-welcome",
                icon: FolderOpenOutline,
            }
            : {
                title: t("execution_guide.get_started.title"),
                description: t("execution_guide.get_started.text"),
                link: "",
                icon: RocketLaunchOutline,
            },
        {
            title: t("execution_guide.workflow_components.title"),
            description: t("execution_guide.workflow_components.text"),
            link: "https://kestra.io/docs/workflow-components?utm_source=app&utm_medium=referral&utm_campaign=onboarding-welcome",
            icon: ViewGridOutline,
        },
        {
            title: t("execution_guide.videos_tutorials.title"),
            description: t("execution_guide.videos_tutorials.text"),
            link: "https://www.youtube.com/watch?v=6TqWWz9difM",
            icon: PlayCircleOutline,
        },
    ])
</script>

<style scoped lang="scss">
.overview-bottom {
    display: flex;
    flex-direction: column;
    border: var(--ks-border-block-primary);
    border-radius: var(--ks-radius-base);
    overflow: hidden;

    > :not(:first-child) {
        border-top: var(--ks-border-block-primary);
    }
}
</style>
