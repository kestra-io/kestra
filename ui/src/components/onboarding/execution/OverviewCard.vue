<template>
    <el-card class="box-card">
        <div class="card-content">
            <div class="">
                <div class="card-header">
                    <el-link underline="never" :href="getLink()" target="_blank">
                        <el-icon class="el-icon--right">
                            <OpenInNew />
                        </el-icon>
                    </el-link>
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
                            <Markdown :source="$t(`execution_guide.${category}.text`)" />
                        </div>
                    </div>
                </div>
                <!-- <div>
                    <h5 class="overview_cat_title">
                        {{ title }}
                    </h5>
                    <div>
                        <Markdown :source="$t(`execution_guide.${category}.text`)" />
                    </div>
                    <el-link underline="never" :href="getLink()" target="_blank">
                        {{ category === 'videos_tutorials' ? $t('watch') : $t('learn_more') }}
                        <el-icon class="el-icon--right">
                            <OpenInNew />
                        </el-icon>
                    </el-link>
                </div> -->
            </div>
        </div>
    </el-card>
</template>

<script setup>
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue";
</script>

<script>
    import Markdown from "../../layout/Markdown.vue";
    import mdiVideoInputComponent from "vue-material-design-icons/videoInputComponent.vue";
    import mdiRocketLaunchOutline from "vue-material-design-icons/rocketLaunchOutline.vue";
    import mdiPlayBoxMultiple from "vue-material-design-icons/playBoxMultiple.vue";

    export default {
        name: "OverviewCard",
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
            content: {
                type: String,
                required: true,
            },
            link: {
                type: String,
                required: true,
            },
        },
        methods: {
            getIcon() {
                return {
                    videos_tutorials: mdiRocketLaunchOutline ,
                    workflow_components: mdiVideoInputComponent,
                    get_started: mdiPlayBoxMultiple,
                }[this.category] ||mdiRocketLaunchOutline ;
            },
            getLink() {
                const links = {
                    videos_tutorials: "https://www.youtube.com/watch?v=6TqWWz9difM",
                    workflow_components: "https://kestra.io/docs/workflow-components",
                    get_started: "https://kestra.io/docs/getting-started/quickstart",
                };
                return links[this.category] || "#"; // Default to "#" if no link is found
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
