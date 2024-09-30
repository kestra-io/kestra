<template>
    <el-collapse accordion v-model="openedDocs">
        <template
            :key="child.title"
            v-for="child in parent.children"
        >
            <el-collapse-item
                class="mt-1"
                :name="child.path"
                v-if="child.children"
            >
                <template #title>
                    <span v-if="disabledPages.includes(child.path)">
                        {{ child.title.capitalize() }}
                    </span>
                    <router-link v-else :to="{path: '/' + child.path}">
                        {{ child.title.capitalize() }}
                    </router-link>
                </template>
                <recursive-toc :parent="child" />
            </el-collapse-item>
            <div v-else>
                <router-link :to="{path: '/' + child.path}">
                    {{ child.title.capitalize() }}
                </router-link>
            </div>
        </template>
    </el-collapse>
</template>

<script>
    export default {
        name: "RecursiveToc",
        props: {
            parent: {
                type: Object,
                required: true
            }
        },
        watch: {
            "$route.path": {
                handler() {
                    this.openedDocs = this.parent.children.filter(child => this.$route.path.includes(child.path)).map(child => child.path);
                },
                immediate: true
            }
        },
        data() {
            return {
                openedDocs: undefined,
                disabledPages: [
                    "docs/api-reference",
                    "docs/terraform/data-sources",
                    "docs/terraform/guides",
                    "docs/terraform/resources"
                ]
            }
        }
    }
</script>

<style lang="scss" scoped>
    .el-collapse {
        --el-collapse-header-font-size: 14px;

        > * {
            font-size: var(--el-collapse-header-font-size);
            line-height: 30px;
        }

        > .el-collapse-item {
            > :deep(button) {
                padding: 0;
            }

            a {
                color: var(--bs-body-color);

                &.router-link-exact-active {
                    font-weight: 700;
                }
            }
        }

        :deep(.el-collapse-item__content) {
            padding-top: 0;
            padding-bottom: 0;
        }
    }
</style>