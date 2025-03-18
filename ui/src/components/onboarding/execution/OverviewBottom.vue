<template>
    <div class="overview-bottom">
        <OverviewCard
            v-for="card in cards"
            :key="card.title"
            :title="card.title"
            :content="card.content"
            :category="card.category"
            :link="card.link"
        />
    </div>
</template>
<script>
    import {mapGetters} from "vuex";
    import OverviewCard from "../execution/OverviewCard.vue";

    export default {
        components: {
            OverviewCard
        },
        data() {
            return {
                cards: [
                    {
                        title: this.$t("execution_guide.get_started.title"),
                        category: "get_started",
                    },
                    {
                        title: this.$t("execution_guide.workflow_components.title"),
                        category: "workflow_components",
                    },
                    {
                        title: this.$t("execution_guide.videos_tutorials.title"),
                        category: "videos_tutorials",
                    }
                ]
            }
        },
        computed: {
            ...mapGetters("core", ["guidedProperties"])
        }
    }
</script>

<style lang="scss" scoped>
    .overview-bottom {
        display: flex;
        gap: 1rem;
        margin-top: 1.5rem;
        justify-items: center;
        flex-wrap: wrap;
        max-width: 1000px;
    }
</style>