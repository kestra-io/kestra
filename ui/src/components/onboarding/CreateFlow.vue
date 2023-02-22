<template>
    <div class="onboarding-main">
        <div class="onboarding-title">
            <h1>🚀 Welcome aboard!</h1>
            <p>
                You're all set up with Kestra, start creating
                your first flow and see the magic in action!
            </p>
        </div>
        <div class="onboarding-content">
            <span class="onboarding-img" />
            <p class="onboarding-content-text">
                To display the dashboard
                you need to create a flow!
            </p>
            <el-button id="create-flow-button" @click="startFlowCreation">
                Create my first flow
            </el-button>
        </div>
        <div class="onboarding-cards">
            <onboarding-card v-for="card in cards" :title="card.title" :content="card.content" :img-class="card.imgClass" :link="card.link" :key="card.title" />
        </div>
    </div>
</template>

<script>
    import {mapGetters} from "vuex";
    import OnboardingCard from "./OnboardingCard.vue";
    import Utils from "../../utils/utils";

    export default {
        name: "CreateFlow",
        components: {
            OnboardingCard
        },
        mounted() {
            document.querySelector("nav").style.display = "none";
        },
        unmounted() {
            document.querySelector("nav").style.display = "flex";
        },
        data() {
            return {
                cards: [
                    {
                        title: "Get started",
                        content: "Ready to dive in? Check out our documentation for step-by-step guides.",
                        imgClass: "onboarding-card-img-get-started",
                        link: "https://kestra.io/docs/getting-started/"
                    },
                    {
                        title: "Watch our Demo Video",
                        content: "Get a glimpse of Kestra's power with our demo video.",
                        imgClass: "onboarding-card-img-demo-video",
                        link: "https://kestra.io/docs/getting-started/"
                    },
                    {
                        title: "Need Help?",
                        content: "Need assistance with a specific feature or flow? Our community of data engineers and developers are here to help.",
                        imgClass: "onboarding-card-img-help",
                        link: "https://api.kestra.io/v1/communities/slack/redirect"
                    }
                ]
            }
        },
        computed: {
            Utils() {
                return Utils
            },
            ...mapGetters("core", ["guidedProperties"])
        },
        methods:{
            startFlowCreation: function () {
                this.$router.push({name:"flows/create"})
            }
        }
    }
</script>

<style scoped lang="scss">
.onboarding-main {
    margin-top: 50px;
}
.onboarding-title {
    width: 418px;
    height: 128px;

    /* h1-font-size */
    h1 {
        font-family: 'Ubuntu';
        font-weight: 700;
        font-size: 40px;
        line-height: 46px;
    }
    p {
        font-family: 'Ubuntu';
        font-weight: 300;
        font-size: 22px;
        line-height: 30px;
        text-align:justify;
        word-break:keep-all;
    }
    html.dark & {
        color: #FFFFFF;
    }
    flex: none;
    order: 0;
    flex-grow: 0;
}
.onboarding-content {
    margin-bottom: 18px;
    padding-bottom: 30px;
    html.dark & {
        background: #202331;
    }
    border-radius: 4px;

    display: flex;
    align-items: center;
    flex-direction: column;
}
.onboarding-img {
    width: 581px;
    height: 263px;
    background: url("../../assets/onboarding/onboarding-light.svg") no-repeat;
    html.dark & {
        background: url("../../assets/onboarding/onboarding-dark.svg") no-repeat;
    }
}

.onboarding-content-text {
    font-style: normal;
    font-weight: 300;
    font-size: 32px;
    line-height: 42px;

    width: 385px;
    text-align: center;

    html.dark & {
        color: #FFFFFF;
    }
}

.onboarding-cards {
    display: flex;
    justify-content: space-between;
}


#create-flow-button {
    width: 249px;
    height: 50px;
    gap: 10px;

    background: #192A4E;

    border: 1px solid #6D81F5;
    border-radius: 4px;
    font-weight: 700;
    font-size: 18px;
    line-height: 21px;
    color: #FFFFFF;
}
</style>