<template>
    <nav class="d-flex align-items-center w-100 gap-3 top-bar">
        <div class="d-flex flex-column flex-grow-1 flex-shrink-1 overflow-hidden top-title">
            <div class="d-flex align-items-end gap-2">
                <SidebarToggleButton
                    v-if="layoutStore.sideMenuCollapsed"
                    @toggle="layoutStore.setSideMenuCollapsed(false)"
                />
                <div class="d-flex flex-column gap-2">
                    <div class="breadcrumb-container d-flex align-items-center">
                        <template v-if="visibleBreadcrumbs && visibleBreadcrumbs.length > 0">
                            <template v-for="(item, x) in visibleBreadcrumbs" :key="x">
                                <el-dropdown
                                    v-if="item.label === '...'"
                                    placement="bottom-start"
                                    popperClass="breadcrumb-dropdown"
                                >
                                    <span class="breadcrumb-link breadcrumb-ellipsis">...</span>
                                    <template #dropdown>
                                        <el-dropdown-menu>
                                            <el-dropdown-item
                                                v-for="(hiddenItem, y) in item.hidden"
                                                :key="y"
                                            >
                                                <RouterLink
                                                    v-if="!hiddenItem.disabled && hiddenItem.link"
                                                    :to="hiddenItem.link"
                                                    class="breadcrumb-link"
                                                >
                                                    {{ hiddenItem.label }}
                                                </RouterLink>
                                                <span
                                                    v-else
                                                    class="breadcrumb-link"
                                                    :class="{'disabled': hiddenItem.disabled}"
                                                >
                                                    {{ hiddenItem.label }}
                                                </span>
                                            </el-dropdown-item>
                                        </el-dropdown-menu>
                                    </template>
                                </el-dropdown>
                                <RouterLink
                                    v-else-if="!item.disabled && item.link"
                                    :to="item.link"
                                    class="breadcrumb-link"
                                >
                                    {{ item.label }}
                                </RouterLink>
                                <span
                                    v-else
                                    class="breadcrumb-link"
                                    :class="{'disabled': item.disabled}"
                                >
                                    {{ item.label }}
                                </span>
                                <span
                                    v-if="x < visibleBreadcrumbs.length - 1"
                                    class="breadcrumb-separator"
                                >/</span>
                            </template>
                        </template>
                    </div>

                    <h1 class="h5 fw-semibold m-0 d-inline-flex align-items-center">
                        <slot name="title">
                            <span class="title-span">{{ title }}</span>
                            <el-tooltip v-if="description" :content="description">
                                <Information class="ms-2 icon" />
                            </el-tooltip>
                            <Badge v-if="beta" label="Beta" />
                        </slot>
                        <el-button
                            class="icon"
                            :class="{'active': bookmarked}"
                            :icon="bookmarked ? StarIcon : StarOutlineIcon"
                            circle
                            @click="onStarClick"
                        />
                    </h1>
                </div>
            </div>
        </div>

        <div class="d-lg-flex side gap-2 flex-shrink-0 align-items-center mycontainer">
            <div class="d-none d-lg-flex align-items-center">
                <GlobalSearch class="trigger-flow-guided-step" />
            </div>
            <div class="d-flex side gap-2 flex-shrink-0 align-items-center">
                <el-button
                    v-if="shouldDisplayDeleteButton && logsStore.logs !== undefined && logsStore.logs.length > 0"
                    @click="deleteLogs"
                >
                    <TrashCan class="me-2" />
                    <span>{{ $t('delete logs') }}</span>
                </el-button>
            </div>
            <slot name="additional-right" />
            <div class="d-flex fixed-buttons icons">
                <Impersonating />
            </div>
        </div>
    </nav>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";
    import {useRoute, RouterLink} from "vue-router";
    import GlobalSearch from "./GlobalSearch.vue";
    import Impersonating from "override/components/auth/Impersonating.vue";
    import TrashCan from "vue-material-design-icons/TrashCan.vue";
    import StarOutlineIcon from "vue-material-design-icons/StarOutline.vue";
    import StarIcon from "vue-material-design-icons/Star.vue";
    import Information from "vue-material-design-icons/Information.vue";
    import Badge from "../global/Badge.vue";
    import {useLogsStore} from "../../stores/logs";
    import {useBookmarksStore} from "../../stores/bookmarks";
    import {useToast} from "../../utils/toast";
    import {useFlowStore} from "../../stores/flow";
    import {useLayoutStore} from "../../stores/layout";
    import SidebarToggleButton from "./SidebarToggleButton.vue";

    type RouterLinkTo = InstanceType<typeof RouterLink>["$props"]["to"];

    const props = defineProps<{
        title: string;
        description?: string;
        breadcrumb?: { label: string; link?: RouterLinkTo; disabled?: boolean; hidden?: any[] }[];
        beta?: boolean;
    }>();

    const logsStore = useLogsStore();
    const bookmarksStore = useBookmarksStore();
    const flowStore = useFlowStore();
    const route = useRoute();
    const layoutStore = useLayoutStore();

    const visibleBreadcrumbs = computed(() => {
        if (!props.breadcrumb || props.breadcrumb.length <= 3) {
            return props.breadcrumb;
        }

        const hiddenItems = props.breadcrumb.slice(1, -1);
        return [
            props.breadcrumb[0],
            {
                label: "...",
                hidden: hiddenItems,
                disabled: true,
            },
            props.breadcrumb[props.breadcrumb.length - 1],
        ];
    });

    const shouldDisplayDeleteButton = computed(() => {
        return route.name === "flows/update" && route.params?.tab === "logs";
    });

    const currentFavURI = computed(() => {
        if (route) {
            return (
                window.location.pathname +
                window.location.search
                    .replace(/&?page=[^&]*/gi, "")
                    .replace(/\?&/, "?")
            );
        }
        return "";
    });

    const bookmarked = computed(() => {
        return bookmarksStore.pages.some((page) => page.path === currentFavURI.value);
    });

    const toast = useToast();
    const {t} = useI18n();

    const deleteLogs = () => {
        if (!flowStore.flow) throw new Error("No flow selected");

        toast.confirm(t("delete_all_logs"), async () => {
            if (!flowStore.flow) return;
            return logsStore.deleteLogs({
                namespace: flowStore.flow?.namespace,
                flowId: flowStore.flow?.id,
            });
        });
    };

    const onStarClick = () => {
        if (bookmarked.value) {
            bookmarksStore.remove({path: currentFavURI.value});
        } else {
            bookmarksStore.add({
                path: currentFavURI.value,
                label: props.breadcrumb?.length
                    ? `${props.breadcrumb[props.breadcrumb.length - 1].label}: ${props.title}`
                    : props.title,
            });
        }
    };
</script>

<style scoped lang="scss">
nav {
    top: 0;
    position: sticky;
    z-index: 1000;
    padding: 1rem 2rem;
    border-bottom: 1px solid var(--ks-border-primary);
    background: var(--ks-background-card);

    .top-title,
    h1,
    .el-breadcrumb {
        white-space: nowrap;
        max-width: 100%;
        text-overflow: ellipsis;
        overflow: hidden;
    }

    .breadcrumb-container {
        min-width: 0;
        flex-shrink: 1;
        overflow: hidden;
    }

    .breadcrumb-link {
        color: var(--ks-text-secondary, #999);
        text-decoration: none;
        white-space: nowrap;
        flex-shrink: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        min-width: 0;
        font-size: var(--font-size-small);

        &:hover:not(.disabled) {
            color: var(--ks-text-primary);
        }

        &.disabled {
            cursor: default;
            color: var(--ks-text-secondary, #999);
        .top-title {
            position: relative;

        &::after {
            content: "";
            position: absolute;
            top: 0;
            right: 0;
            width: 40px;
            height: 100%;
            background: linear-gradient(to left, var(--ks-background-card), transparent);
            pointer-events: none;
            }
        }

        h1 {
            line-height: 1.6;
            display: flex !important;
            align-items: center;
        }
    }

    .breadcrumb-separator {
        color: var(--ks-text-secondary, #999);
        margin: 0 0.5rem;
        flex-shrink: 0;
    }

    .breadcrumb-ellipsis {
        color: var(--ks-text-secondary, #999);
        flex-shrink: 0;
        cursor: pointer;
    }

    .title-span {
        font-size: var(--font-size-small);
    }

    .star-button {
        margin-left: 0.5rem;
        border: none;
        flex-shrink: 0;
    }

    .star-active {
        color: #9470FF;
    }

    .side {
        .fixed-buttons {
            align-items: center;

            button,
            :deep(button),
            a,
            :deep(a) {
                border: none;
                font-size: var(--font-size-lg);
                padding: 0.25rem;
            }
        }

        :slotted(ul),
        :deep(ul) {
            display: flex;
            list-style: none;
            padding: 0;
            margin: 0;
            gap: 0.5rem;
            align-items: center;
        }
    }

    @media (max-width: 768px) {
        .mycontainer {
            display: grid;
            grid-template-columns: repeat(3, minmax(0, auto));
            grid-template-rows: repeat(2, auto);
            gap: 10px;
            overflow: hidden;
        }

        .icons {
            grid-row: 2;
            grid-column: 2;
            display: contents;
        }
    }

    @media (max-width: 664px) {
        .mycontainer {
            display: grid;
            grid-template-columns: repeat(2, minmax(0, auto));
            grid-template-rows: repeat(2, auto);
            gap: 10px;
            overflow: hidden;
        }
    }
}

:deep(.breadcrumb-dropdown) {
    .el-dropdown-menu__item {
        padding: 0;

        .breadcrumb-link {
            padding: 0.5rem 1rem;
            display: block;
            width: 100%;

        @media (max-width: 992px) {
            padding: 0.75rem 1.5rem;
        }

        @media (max-width: 768px) {
            padding: 0.4rem 0.75rem;

            .mycontainer {
                display: grid;
                grid-template-columns: repeat(3, minmax(0, auto));
                grid-template-rows: repeat(2, auto);
                gap: 10px;
                overflow: hidden;
            }
            .icons {
                grid-row: 2;
                grid-column: 2;
                display: contents;
            }
        }
        @media (max-width: 664px) {
            padding: 0.3rem 0.5rem;
            
            .mycontainer {
                display: grid;
                grid-template-columns: repeat(2, minmax(0, auto));
                grid-template-rows: repeat(2, auto);
                gap: 10px;
                overflow: hidden;
            }
        }
    }
}}}
</style>