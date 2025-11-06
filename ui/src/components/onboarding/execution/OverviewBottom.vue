<template>
    <div class="overview-bottom">
        <OverviewCard
            v-for="(card, index) in cards"
            :key="card.title"
            :title="card.title"
            :content="card.content"
            :category="card.category"
            :link="card.link"
            :icon="card.icon"
            :showIcon="index >= 1"
        />
    </div>
</template>
<script setup lang="ts">
    import {useI18n} from "vue-i18n";
    import OverviewCard from "../execution/OverviewCard.vue";
    import PlayBoxMultiple from "vue-material-design-icons/PlayBoxMultiple.vue";
    import RocketLaunchOutline from "vue-material-design-icons/RocketLaunchOutline.vue";
    import VideoInputComponent from "vue-material-design-icons/VideoInputComponent.vue";

    const {t} = useI18n();

    const cards = [
        {
            title: t("execution_guide.get_started.title"),
            category: "get_started",
            content: "",
            link: "",
            icon: RocketLaunchOutline,
        },
        {
            title: t("execution_guide.workflow_components.title"),
            category: "workflow_components",
            content: "",
            link: "https://kestra.io/docs/workflow-components",
            icon: VideoInputComponent,
        },
        {
            title: t("execution_guide.videos_tutorials.title"),
            category: "videos_tutorials",
            content: "",
            link: "https://www.youtube.com/watch?v=6TqWWz9difM",
            icon: PlayBoxMultiple,
        }
    ];
</script>

<style scoped lang="scss">
.overview-bottom {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 1rem;
    margin-top: 1.5rem;
    justify-items: center;

    @media (max-width: 991px) {
        grid-template-columns: 1fr;
    }

    @media (min-width: 992px) and (max-width: 1299px) {
        grid-template-columns: repeat(2, 1fr);

        > :last-child {
            grid-column: 1 / -1;
        }
    }
}
</style>
