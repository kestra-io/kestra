<template>
    <div class="onboarding-bottom">
        <onboarding-card
            v-for="card in cards"
            :key="card.title"
            :title="card.title"
            :content="card.content"
            :category="card.category"
            :link="card.link"
            @click="handleCardClick(card)"
        />
    </div>
</template>
<script>
    import {mapGetters} from "vuex";
    import OnboardingCard from "./OnboardingCard.vue";

    export default {
        components: {
            OnboardingCard
        },
        data() {
            return {
                cards: [
                    {
                        title: this.$t("welcome.tour.title"),
                        category: "tour",

                    },
                    {
                        title: this.$t("welcome.tutorial.title"),
                        category: "tutorial",
                    },
                    {
                        title: this.$t("welcome.help.title"),
                        category: "help",
                    }
                ]
            }
        },
        computed: {
            ...mapGetters("core", ["guidedProperties"])
        },
        methods: {
            startTour() {
                localStorage.setItem("tourDoneOrSkip", undefined);
                this.$store.commit("core/setGuidedProperties", {tourStarted: false});
                this.$tours["guidedTour"]?.start();
            },
            handleCardClick(card) {
                if (card.category === "tour") {
                    this.startTour();
                } else if (card.category === "help") {
                    window.open("https://kestra.io/slack", "_blank");
                }
            }
        }
    }
</script>

<style lang="scss" scoped>
    .onboarding-bottom {
        display: flex;
        gap: 1rem;
        margin-top: 1.5rem;
        justify-items: center;
        flex-wrap: wrap;
        max-width: 1000px;
    }
</style>