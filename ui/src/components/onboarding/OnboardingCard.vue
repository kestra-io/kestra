<template>
    <el-card>
        <template #header>
            <img :src="img" alt="">
        </template>
        <div class="content row">
            <p class="fw-bold text-uppercase smaller-text">
                {{ title }}
            </p>
            <markdown :source="mdContent" class="mt-4" />
        </div>
    </el-card>
</template>

<script>
    import imageStarted from "../../assets/onboarding/onboarding-started-dark.svg"
    import imageHelp from "../../assets/onboarding/onboarding-help-dark.svg"
    import imageDoc from "../../assets/onboarding/onboarding-docs-dark.svg"
    import imageProduct from "../../assets/onboarding/onboarding-product-dark.svg"
    import Markdown from "../layout/Markdown.vue";

    export default {
        name: "OnboardingCard",
        components: {Markdown},
        props: {
            title: {
                type: String,
                required: true
            },
            category: {
                type: String,
                required: true
            }
        },
        created() {
            this.loadMarkdown();
        },
        data() {
            return {
                markdownContent: "",
            }
        },
        methods: {
            loadMarkdown() {
                import(`../../assets/onboarding/markdown/${this.category}${this.lang}.md?raw`)
                    .then((module) => {
                        this.markdownContent = module.default;
                    })
            }
        },
        computed: {
            lang() {
                const lang = localStorage.getItem("lang") || "en";
                if (lang === "fr") {
                    return "_fr"
                }
                return ""
            },
            img() {
                switch (this.category) {
                case "started":
                    return imageStarted;
                case "help":
                    return imageHelp;
                case "docs":
                    return imageDoc;
                case "product":
                    return imageProduct;
                }
                return imageStarted
            },
            mdContent() {
                return this.markdownContent;
            }
        }
    }
</script>

<style scoped lang="scss">
    .el-card {

        &:deep(.el-card__header) {
            padding: 0;
        }

        position: relative;
        height: 100%;
        cursor: pointer;
    }

    .smaller-text {
        font-size: 0.86em;
    }

    p {
        margin-bottom: 0;
    }

    img {
        width: 100%;
        height: 100%;
    }
</style>