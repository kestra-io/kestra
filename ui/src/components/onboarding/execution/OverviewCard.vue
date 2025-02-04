<template>
    <el-card class="box-card">
        <div>
            <div class="overview-title">
                <div>
                    <h5 class="overview_cat_title">
                        {{ title }}
                    </h5>
                    <div>
                        <markdown :source="$t(`execution_guide.${category}.text`)" />
                    </div>
                    <el-link :underline="false" :href="getLink()" target="_blank">
                        {{ category === 'videos_tutorials' ? 'Watch' : 'Learn More' }}
                        <el-icon class="el-icon--right">
                            <OpenInNew />
                        </el-icon>
                    </el-link>
                </div>
            </div>
        </div>
    </el-card>
</template>

<script setup>
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue";
</script>

<script>
    import Markdown from "../../layout/Markdown.vue";

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
        },
        methods: {
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
.el-card {
	background-color: var(--ks-background-card);
	border-color: var(--ks-border-primary);
	box-shadow: var(--el-box-shadow);
	position: relative;
	width: 100px;
	height: 180px;
	min-width: 200px;
	flex: 1;
	cursor: pointer;

	&:deep(.el-card__header) {
		padding: 0;
	}

	&:deep(.el-link) {
		position: absolute;
		bottom: 15px;
		font-size: 12px;
		border: 1px solid var(--ks-border-primary);
		padding: 3px 10px;
		border-radius: 5px;

		&:hover {
			color: var(--bs-gray-900-lighten-7);
		}
	}
}

.box-card {
	.card-header {
		position: absolute;
		top: 5px;
		right: 5px;
	}

	.overview_cat_title {
		width: 100%;
		margin: 3px 0 10px;
		font-weight: 600;
		font-size: var(--el-font-size-small);
	}
}

.overview-title {
	display: inline-flex;
}

:deep(.markdown) {
	font-size: var(--el-font-size-extra-small) !important;
	color: var(--ks-content-tertiary);
}

</style>