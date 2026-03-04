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
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";
    import {useRouter} from "vue-router";
    import OnboardingCard, {OnboardingCardModel} from "../../components/onboarding/OnboardingCard.vue";

    const {t} = useI18n();
    const router = useRouter();

    const cards = computed((): OnboardingCardModel[] => [
        {title: t("welcome.tour.title"), category: "tour"},
        {title: t("welcome.tutorial.title"), category: "tutorial"},
        {title: t("welcome.help.title"), category: "help"}
    ]);

    const handleCardClick = (card: OnboardingCardModel) => {
        if (card.category === "tour") void router.push({name: "flows/create"});
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
