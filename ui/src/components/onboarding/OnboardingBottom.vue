<template>
    <el-row justify="space-between" :gutter="20">
        <el-col
            v-for="card in cards"
            :key="card.title"
            :xs="24"
            :sm="12"
            :md="12"
            :lg="6"
            :xl="6"
            class="pb-4"
        >
            <onboarding-card
                :title="card.title"
                :content="card.content"
                :category="card.category"
                :link="card.link"
            />
        </el-col>
    </el-row>
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
                        title: this.$t("welcome.started.title"),
                        category: "started",
                    },
                    {
                        title: this.$t("welcome.product-tour.title"),
                        category: "product",

                    },
                    {
                        title: this.$t("welcome.doc.title"),
                        category: "docs",
                    },
                    {
                        title: this.$t("welcome.need-help.title"),
                        category: "help",
                    }
                ]
            }
        },
        computed: {
            ...mapGetters("core", ["guidedProperties"])
        }
    }
</script>