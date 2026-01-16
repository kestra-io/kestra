<template>
    <div class="onboarding-bottom">
        <OnboardingCard
            v-for="card in cards"
            :key="card.title"
            v-bind="card"
            @click="handleCardClick(card)"
        />
    </div>
</template>
<script setup lang="ts">
    import {computed, getCurrentInstance} from "vue";
    import {useI18n} from "vue-i18n";
    import {useCoreStore} from "../../stores/core";
    import OnboardingCard, {OnboardingCardModel} from "../../components/onboarding/OnboardingCard.vue";

    const {t} = useI18n();
    const coreStore = useCoreStore();
    const instance = getCurrentInstance();

    const cards = computed((): OnboardingCardModel[] => [
        {title: t("welcome.tour.title"), category: "tour"},
        {title: t("welcome.tutorial.title"), category: "tutorial"},
        {title: t("welcome.help.title"), category: "help"}
    ]);
    const startTour = () => {
        localStorage.setItem("tourDoneOrSkip", "undefined");
        coreStore.guidedProperties = {
            ...coreStore.guidedProperties,
            tourStarted: true
        };
        (instance?.proxy as any)?.$tours["guidedTour"]?.start();
    };

    const handleCardClick = (card: OnboardingCardModel) => {
        if (card.category === "tour") startTour();
        else if (card.category === "help") window.open("https://kestra.io/slack", "_blank");
    };
</script>

<style scoped lang="scss">
    .onboarding-bottom {
        display: flex;
        gap: 1rem;
        margin-top: 1.5rem;
        justify-items: center;
        flex-wrap: wrap;
        max-width: 1000px;
    }
</style>