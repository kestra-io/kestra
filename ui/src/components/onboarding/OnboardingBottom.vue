<template>
    <el-col class="main-col mt-4">
        <el-row :gutter="25" justify="center">
            <el-col
                v-for="card in cards"
                :key="card.title"
                :lg="8"
                :md="24"
                :offset="32"
            >
                <onboarding-card
                    :title="card.title"
                    :content="card.content"
                    :img-class="card.imgClass"
                    :link="card.link"
                />
            </el-col>
        </el-row>
    </el-col>
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
                        title: this.$t("get started"),
                        content: this.$t("get started content"),
                        imgClass: "get-started",
                        link: "https://kestra.io/docs/getting-started/"
                    },
                    {
                        title: this.$t("watch demo"),
                        content: this.$t("watch demo content"),
                        imgClass: "demo-video",
                        link: "https://api.kestra.io/r/onboarding-video"
                    },
                    {
                        title: this.$t("need help?"),
                        content: this.$t("need help? content"),
                        imgClass: "help",
                        link: "https://api.kestra.io/v1/communities/slack/redirect"
                    }
                ]
            }
        },
        computed: {
            ...mapGetters("core", ["guidedProperties"])
        }
    }
</script>

