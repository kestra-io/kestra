<template>
    <el-col class="main-col mt-4">
        <el-row :gutter="25" justify="center">
            <el-col
                v-for="card in cards"
                :key="card.title"
                :lg="8"
                :md="24"
                :offset="32"
                class="mb-4"
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
                        imgClass: "started",
                        link: "https://kestra.io/docs/getting-started/?utm_source=app&utm_content=welcome"
                    },
                    {
                        title: this.$t("watch demo"),
                        content: this.$t("watch demo content"),
                        imgClass: "demo",
                        link: "<iframe width=\"100%\" height=\"100%\" src=\"https://www.youtube.com/embed/yuV_rgnpXU8\" frameborder=\"0\" allow=\"accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share\" allowfullscreen></iframe>"

                    },
                    {
                        title: this.$t("need help?"),
                        content: this.$t("need help? content"),
                        imgClass: "help",
                        link: "https://kestra.io/slack?utm_source=app&utm_content=welcome"
                    }
                ]
            }
        },
        computed: {
            ...mapGetters("core", ["guidedProperties"])
        }
    }
</script>

