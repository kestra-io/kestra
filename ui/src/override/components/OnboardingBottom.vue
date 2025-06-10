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
<script setup lang="ts">
    import {computed, getCurrentInstance} from "vue";
    import {useI18n} from "vue-i18n";
    import {useStore} from "vuex";
    import OnboardingCard from "../../components/onboarding/OnboardingCard.vue";

    const {t} = useI18n();
    const store = useStore();
    const instance = getCurrentInstance();

    interface Card {
        title: string;
        category: string;
        content?: string;
        link?: string;
    }

    const cards = computed((): Card[] => [
        {title: t("welcome.tour.title"), category: "tour"},
        {title: t("welcome.tutorial.title"), category: "tutorial"},
        {title: t("welcome.help.title"), category: "help"}
    ]);
    const startTour = () => {
        localStorage.setItem("tourDoneOrSkip", "undefined");
        store.commit("core/setGuidedProperties", {tourStarted: true});
        (instance?.proxy as any)?.$tours["guidedTour"]?.start();
    };

    const handleCardClick = (card: Card) => {
        if (card.category === "tour") startTour();
        else if (card.category === "help") window.open("https://kestra.io/slack", "_blank");
    };
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