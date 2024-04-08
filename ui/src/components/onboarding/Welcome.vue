<template>
    <el-col class="main">
        <el-row :gutter="20">
            <el-col :xs="24" :sm="24" :md="24" :lg="14" :xl="14" class="mb-4">
                <el-card class="px-3 pt-4">
                    <el-row justify="space-around" class="p-5">
                        <el-col :xs="24" :sm="24" :md="24" :lg="12" :xl="12" justify="space-between">
                            <el-row class="mb-5" justify="center">
                                <img class="img-fluid" :src="onboardingImage" alt="Kestra Logo">
                            </el-row>
                            <el-row justify="center">
                                <router-link :to="{name: 'flows/create'}">
                                    <el-button size="large" type="primary">
                                        <Plus />
                                        {{ $t("welcome button create") }}
                                    </el-button>
                                </router-link>
                            </el-row>
                        </el-col>
                        <el-col :xs="24" :sm="24" :md="24" :lg="12" :xl="12" justify="center" class="mt-4">
                            <img :src="codeImage" class="img-fluid" alt="code example">
                        </el-col>
                    </el-row>
                </el-card>
            </el-col>
            <el-col :xs="24" :sm="24" :md="24" :lg="10" :xl="10" class="mb-4">
                <iframe
                    width="100%"
                    height="100%"
                    src="https://www.youtube.com/embed/yuV_rgnpXU8"
                    frameborder="0"
                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                    allowfullscreen
                />
            </el-col>
        </el-row>
        <onboarding-bottom />
    </el-col>
</template>

<script>
    import {mapGetters} from "vuex";
    import Plus from "vue-material-design-icons/Plus.vue";
    import OnboardingBottom from "./OnboardingBottom.vue";
    import onboardingImage from "../../assets/onboarding/onboarding-dark.svg"
    import codeImage from "../../assets/onboarding/onboarding-code-dark.svg"

    export default {
        name: "CreateFlow",
        components: {
            OnboardingBottom,
            Plus
        },
        data() {
            return {
                onboardingImage,
                codeImage
            }
        },
        computed: {
            ...mapGetters("core", ["guidedProperties"])
        }
    }
</script>

<style scoped lang="scss">
    .main {
        margin-left: 5em;
        margin-right: 5em;
        margin-top: 10em;

        @media (min-width: 768px) {
            margin-left: 8em;
            margin-right: 8em;
        }

        @media (min-width: 1200px) {
            margin-left: 13em;
            margin-right: 13em;
        }
    }

    .img-fluid {
        max-width: 100%;
        height: auto;
    }

    .el-button {
        font-size: var(--font-size-lg);
        margin-bottom: calc(var(--spacer) * 2);
    }
</style>