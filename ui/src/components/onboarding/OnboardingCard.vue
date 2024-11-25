<template>
    <el-card class="box-card">
        <div class="card-content">
            <div class="card-header">
                <el-link v-if="isOpenInNewCategory" :underline="false" :icon="OpenInNew" :href="getLink()" target="_blank" />
            </div>
            <div class="icon-title">
                <el-icon size="25px">
                    <component :is="getIcon()" />
                </el-icon>
                <div class="card">
                    <h5 class="cat_title">
                        {{ title }}
                    </h5>
                    <div class="cat_description">
                        <markdown :source="mdContent" />
                    </div>
                </div>
            </div>
        </div>
    </el-card>
</template>


<script setup>
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"
    import Monitor from "vue-material-design-icons/Monitor.vue"
    import Slack from "vue-material-design-icons/Slack.vue"
    import PlayBox from "vue-material-design-icons/PlayBoxMultiple.vue"
</script>
<script>

    import Markdown from "../layout/Markdown.vue";
    import Utils from "../../utils/utils.js";

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
            },
            getIcon() {
                switch (this.category) {
                case "help":
                    return Slack;
                case "docs":
                    return PlayBox;
                case "product":
                    return Monitor;
                default:
                    return Monitor;
                }
            },
            getLink() {
                // Define links for the specific categories
                const links = {
                    help: "https://kestra.io/slack",
                    docs: "https://kestra.io/docs"
                };
                return links[this.category] || "#"; // Default to "#" if no link is found
            }
        },
        computed: {
            lang() {
                const lang = Utils.getLang();
                if (lang === "fr") {
                    return "_fr"
                }
                return ""
            },
            mdContent() {
                return this.markdownContent;
            },
            isOpenInNewCategory() {
                // Define which categories should show the OpenInNew icon
                return this.category === "help" || this.category === "docs";
            }
        }
    }
</script>

<style scoped lang="scss">
    a:hover {
        text-decoration: none;
    }

    .el-card {
        background-color: var(--bs-gray-100);
        border-color: var(--el-border-color);
        box-shadow: var(--el-box-shadow);
        position: relative;
        height: 100%;
        cursor: pointer;

        &:deep(.el-card__header) {
            padding: 0;
        }
    }

    .box-card {
        .card-header {
            position: absolute;
            top: 5px;
            right: 5px;
        }

        .cat_title {
            width: 100%;
            margin: 3px 0 10px;
            padding-left: 20px;
            font-weight: 600;
            font-size: var(--el-font-size-small);
        }

        .cat_description {
            width: 100%;
            margin: 0;
            padding-left: 20px;
        }
    }

    .icon-title {
        display: inline-flex;

        &.icon-title-left {
            margin-right: 10px;
        }
    }

    .el-link {
        font-size: 20px;
    }
</style>