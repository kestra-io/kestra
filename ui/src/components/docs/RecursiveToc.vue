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
                <recursive-toc :parent="child" :makeIndexNavigable="makeIndexNavigable">
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

<script setup lang="ts">
    import {ref} from "vue";

    const disabledPages = [
        "docs/api-reference",
        "docs/terraform/data-sources",
        "docs/terraform/guides",
        "docs/terraform/resources"
    ]

    defineProps({
        parent: {
            type: Object as () => {children?: {path: string, title: string, children?: any[]}[]},
            required: true
        },
        makeIndexNavigable: {
            type: Boolean,
            default: true
        }
    })

    const openedDocs = ref<string[]>([]);

</script>

<style scoped lang="scss">
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