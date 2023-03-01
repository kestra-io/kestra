<template>
    <el-card @click="openLink">
        <Export class="icon" />
        <el-row :gutter="12" align="middle">
            <el-col :md="6" :lg="8">
                <div :class="imgClass" />
            </el-col>
            <el-col :md="12" :lg="14">
                <h3>{{ title }}</h3>
                <p>{{ content }}</p>
            </el-col>
        </el-row>
    </el-card>
</template>

<script>
    import Export from "vue-material-design-icons/Export.vue";

    export default {
        name: "OnboardingCard",
        components: {
            Export,
        },
        props: {
            title: {
                type: String,
                required: true
            },
            content: {
                type: String,
                required: true
            },
            imgClass: {
                type: String,
                required: true
            },
            link: {
                type: String,
                required: true
            }
        },
        methods: {
            openLink() {
                window.open(this.link, "_blank");
            }
        }
    }
</script>

<style scoped lang="scss">
    .el-card {
        padding: var(--spacer);
        position: relative;
        height: 100%;
        cursor: pointer;
    }

    p {
        color: var(--bs-gray-500);
    }

    .icon {
        position: absolute;
        top: var(--spacer);
        right: var(--spacer);
        color: var(--bs-gray-500);
        font-size: var(--font-size-lg);
    }

    .onboarding-card-img {
        // Image size
        min-height: 130px;
        height: 100%;
    }

    .onboarding-card-img-get-started {
        @extend .onboarding-card-img;
        background: url("../../assets/onboarding/onboarding-started-light.svg") no-repeat center;

        html.dark & {
            background: url("../../assets/onboarding/onboarding-started-dark.svg") no-repeat center;
        }
    }

    .onboarding-card-img-demo-video {
        @extend .onboarding-card-img;
        background: url("../../assets/onboarding/onboarding-demo-light.svg") no-repeat center;

        html.dark & {
            background: url("../../assets/onboarding/onboarding-demo-dark.svg") no-repeat center;
        }
    }

    .onboarding-card-img-help {
        @extend .onboarding-card-img;
        background: url("../../assets/onboarding/onboarding-help-light.svg") no-repeat center;

        html.dark & {
            background: url("../../assets/onboarding/onboarding-help-dark.svg") no-repeat center;
        }
    }
</style>