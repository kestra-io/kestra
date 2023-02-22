<template>
    <a :href="link" target="_blank" class="onboarding-card-main">
        <Export class="icon" />
        <div :class="imgClass" />
        <div class="onboarding-card-content">
            <h3>{{ title }}</h3>
            <p>{{ content }}</p>
        </div>
    </a>
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
    }
</script>

<style scoped lang="scss">
    .onboarding-card-main {
        position: relative;
        display: flex;
        flex-direction: row;
        padding: 30px 16px;

        cursor: pointer;

        width: 380px;
        height: 210px;

        html.dark & {
            color: #FFFFFF;
            background: #202331;
            border: 1px solid #292E40;
        }

        border-radius: 4px;
        flex-grow: 1;
        margin: 0 32px;
    }
    .onboarding-card-main:first-of-type{
        margin-left: 0px;
    }
    .onboarding-card-main:last-of-type{
        margin-right: 0px;
    }
    h3 {
        font-weight: 700;
        font-size: 24px;
        line-height: 23px;
    }
    p {
        font-weight: 400;
        font-size: 20px;
        line-height: 24px;
    }
    .icon {
        position: absolute;
        top: 13px;
        right: 13px;
        font-size: 20px;
    }

    .onboarding-card-content{
        width: 250px;
    }

    .onboarding-card-img{
        width: 150px;
        height: 150px;
    }
    .onboarding-card-img-get-started {
        @extend  .onboarding-card-img;
        background: url("../../assets/onboarding/onboarding-started-light.svg") no-repeat;
        html.dark & {
            background: url("../../assets/onboarding/onboarding-started-dark.svg") no-repeat;
        }
    }

    .onboarding-card-img-demo-video {
        @extend  .onboarding-card-img;
        background: url("../../assets/onboarding/onboarding-demo-light.svg") no-repeat;
        html.dark & {
            background: url("../../assets/onboarding/onboarding-demo-dark.svg") no-repeat;
        }
    }

    .onboarding-card-img-help {
        @extend  .onboarding-card-img;
        background: url("../../assets/onboarding/onboarding-help-light.svg") no-repeat;
        html.dark & {
            background: url("../../assets/onboarding/onboarding-help-dark.svg") no-repeat;
        }
    }
</style>