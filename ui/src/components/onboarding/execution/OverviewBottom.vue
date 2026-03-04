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
            :showIcon="index === 0 ? props.isNamespace : true"
        />
    </div>
</template>
<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n";
    import OverviewCard from "../execution/OverviewCard.vue";
    import PlayBoxMultiple from "vue-material-design-icons/PlayBoxMultiple.vue";
    import RocketLaunchOutline from "vue-material-design-icons/RocketLaunchOutline.vue";
    import VideoInputComponent from "vue-material-design-icons/VideoInputComponent.vue";
    import FolderOpenOutline from "vue-material-design-icons/FolderOpenOutline.vue";

    const {t} = useI18n();

    const props = withDefaults(defineProps<{isNamespace?: boolean}>(), {
        isNamespace: false,
    })

    const cards = computed(() => {
        const baseCards = [
            props.isNamespace
                ? {
                    title: t("execution_guide.namespaces.title"),
                    category: "namespaces",
                    content: "",
                    link: "https://kestra.io/docs/ui/namespaces",
                    icon: FolderOpenOutline,
                }
                : {
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
        return baseCards;
    });
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
