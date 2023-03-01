<template>
    <el-card @click="openLink">
        <Export class="icon" />
        <div class="content">
            <div>
                <div class="img" :class="imgClass" />
            </div>
            <div>
                <h3>{{ title }}</h3>
                <p>{{ content }}</p>
            </div>

        </div>

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
        margin-bottom: 0;
    }

    .icon {
        position: absolute;
        top: calc(var(--spacer) / 2);
        right: calc(var(--spacer) / 2);
        color: var(--bs-gray-500);
        font-size: var(--font-size-lg);
    }

    .content {
        display: flex;

        > div:first-child {
            min-width: 160px;
            flex-wrap: nowrap;
        }
    }

    div.img {
        min-height: 130px;
        height: 100%;

        &.get-started {
            background: url("../../assets/onboarding/onboarding-started-light.svg") no-repeat top left;

            html.dark & {
                background: url("../../assets/onboarding/onboarding-started-dark.svg") no-repeat top left;
            }
        }

        &.demo-video {
            background: url("../../assets/onboarding/onboarding-demo-light.svg") no-repeat top left;

            html.dark & {
                background: url("../../assets/onboarding/onboarding-demo-dark.svg") no-repeat top left;
            }
        }

        &.help {
            background: url("../../assets/onboarding/onboarding-help-light.svg") no-repeat top left;

            html.dark & {
                background: url("../../assets/onboarding/onboarding-help-dark.svg") no-repeat top left;
            }
        }
    }
</style>