<template>
    <el-card class="box-card">
        <div class="card-content">
            <div class="card-header">
                <el-link
                    v-if="isOpenInNewCategory"
                    :underline="false"
                    :icon="OpenInNew"
                    :href="getLink()"
                    target="_blank"
                />
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
                        <markdown :source="$t(`welcome.${category}.text`)" />
                    </div>
                </div>
            </div>
        </div>
    </el-card>
</template>

<script setup>
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue";
    import Monitor from "vue-material-design-icons/Monitor.vue";
    import Slack from "./components/SlackLogo.vue";
    import PlayBox from "vue-material-design-icons/PlayBoxMultiple.vue";
</script>
<script>
    import Markdown from "../layout/Markdown.vue";

    export default {
        name: "OnboardingCard",
        components: {Markdown},
        props: {
            title: {
                type: String,
                required: true,
            },
            category: {
                type: String,
                required: true,
            },
        },

        methods: {
            getIcon() {
                switch (this.category) {
                case "help":
                    return Slack;
                case "tutorial":
                    return PlayBox;
                case "tour":
                    return Monitor;
                default:
                    return Monitor;
                }
            },
            getLink() {
                const links = {
                    help: "https://kestra.io/slack",
                };
                return links[this.category] || "#";
            },
        },
        computed: {
            isOpenInNewCategory() {
                return this.category === "help";
            },
        },
    };
</script>

<style scoped lang="scss">
a:hover {
    text-decoration: none;
}

.el-card {
    background-color: var(--ks-background-card);
    border-color: var(--ks-border-primary);
    box-shadow: var(--el-box-shadow);
    position: relative;
    min-width: 250px;
    flex: 1;
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
