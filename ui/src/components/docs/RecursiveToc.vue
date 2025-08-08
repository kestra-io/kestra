<template>
    <el-collapse accordion v-model="openedDocs" :key="openedDocs">
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
                    <span v-if="disabledPages.includes(child.path) || !makeIndexNavigable">
                        {{ child.title.capitalize() }}
                    </span>
                    <slot v-else v-bind="child">
                        <router-link :to="{path: '/' + child.path}">
                            {{ child.title.capitalize() }}
                        </router-link>
                    </slot>
                </template>
                <recursive-toc :parent="child" :make-index-navigable="makeIndexNavigable">
                    <template #default="subChild">
                        <slot v-bind="subChild" />
                    </template>
                </recursive-toc>
            </el-collapse-item>
            <div v-else>
                <slot v-bind="child">
                    <router-link :to="{path: '/' + child.path}">
                        {{ child.title.capitalize() }}
                    </router-link>
                </slot>
            </div>
        </template>
    </el-collapse>
</template>

<script>
    import path from "path-browserify";

    export default {
        name: "RecursiveToc",
        props: {
            parent: {
                type: Object,
                required: true
            },
            makeIndexNavigable: {
                type: Boolean,
                default: true
            }
        },
        watch: {
            "$route.path": {
                handler() {
                    const normalizedPath = path.normalize(this.$route.path);
                    this.openedDocs = this.parent.children.filter(child => normalizedPath.includes(child.path)).map(child => child.path);
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
                color: var(--ks-content-primary);

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