<template>
    <el-col class="main-col">
        <div>
            <h1>{{ $t('welcome aboard') }}</h1>
            <h4>
                {{ $t('welcome aboard content') }}
            </h4>
        </div>
        <el-row>
            <el-col :lg="24">
                <el-card>
                    <el-row justify="center">
                        <span class="onboarding-img" />
                    </el-row>
                    <el-row justify="center">
                        <el-col :span="8">
                            <h3 v-html="$t('welcome display require')" />
                        </el-col>
                    </el-row>
                    <el-row justify="center">
                        <router-link :to="{name: 'flows/create'}">
                            <el-button size="large" type="info">
                                {{ $t('welcome button create') }}
                            </el-button>
                        </router-link>
                    </el-row>
                </el-card>
            </el-col>
        </el-row>
        <el-row :gutter="32" justify="center">
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
        name: "CreateFlow",
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

<style scoped lang="scss">
    .main-col {
        padding-top: calc(var(--spacer) * 2);
    }

    h3 {
        text-align: center;
    }

    h3, h4 {
        line-height: var(--line-height-lg);
    }

    .el-col {
        margin-bottom: var(--spacer);
    }

    h4 {
        margin-bottom: var(--spacer);
    }

    .onboarding-img {
        height: 300px;
        width: 100%;
        background: url("../../assets/onboarding/onboarding-light.svg") no-repeat center;

        html.dark & {
            background: url("../../assets/onboarding/onboarding-dark.svg") no-repeat center;
        }
    }

    .el-button {
        font-size: var(--font-size-lg);
        margin-bottom: calc(var(--spacer) * 2);
    }
</style>