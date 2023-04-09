<template>
    <el-col class="mt-4">
        <div>
            <h1>{{ $t('welcome aboard') }}</h1>
            <h4>
                {{ $t('welcome aboard content') }}
            </h4>
        </div>
        <el-row class="mt-4">
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
                    <el-row justify="center" class="mt-4">
                        <router-link :to="{name: 'flows/create'}">
                            <el-button size="large" type="primary">
                                {{ $t('welcome button create') }}
                            </el-button>
                        </router-link>
                    </el-row>
                </el-card>
            </el-col>
        </el-row>
        <onboarding-bottom />
    </el-col>
</template>

<script>
    import {mapGetters} from "vuex";
    import OnboardingBottom from "./OnboardingBottom.vue";

    export default {
        name: "CreateFlow",
        components: {
            OnboardingBottom
        },
        computed: {
            ...mapGetters("core", ["guidedProperties"])
        }
    }
</script>

<style scoped lang="scss">
    h3 {
        text-align: center;
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