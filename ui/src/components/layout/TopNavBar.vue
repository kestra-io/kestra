<template>
    <nav class="d-flex align-items-center w-100 gap-3 top-bar">
        <SidebarToggleButton
            v-if="layoutStore.sideMenuCollapsed"
            @toggle="layoutStore.setSideMenuCollapsed(false)"
        />
        <div class="d-flex flex-column flex-grow-1 flex-shrink-1 overflow-hidden top-title">
            <div class="d-flex align-items-end gap-2">
                <div class="d-flex flex-column gap-2">
                    <el-breadcrumb v-if="breadcrumb">
                        <el-breadcrumb-item v-for="(item, x) in breadcrumb" :key="x" :class="{'pe-none': item.disabled}">
                            <a v-if="item.disabled || !item.link">
                                {{ item.label }}
                            </a>
                            <RouterLink v-else :to="item.link">
                                {{ item.label }}
                            </RouterLink>
                        </el-breadcrumb-item>
                    </el-breadcrumb>
                    <h1 class="h5 fw-semibold m-0 d-inline-flex">
                        <slot name="title">
                            {{ title }}
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
                    <div class="description">
                        <slot name="description">
                            {{ longDescription }}
                        </slot>
                    </div>
                </div>
            </div>
        </div>
        <div class="d-lg-flex side gap-2 flex-shrink-0 align-items-center mycontainer">
            <div class="d-none d-lg-flex align-items-center">
                <GlobalSearch class="trigger-flow-guided-step" />
            </div>
            <div class="d-flex side gap-2 flex-shrink-0 align-items-center">
                <el-button v-if="shouldDisplayDeleteButton && logsStore.logs !== undefined && logsStore.logs.length > 0" @click="deleteLogs()">
                    <TrashCan class="me-2" />
                    <span>{{ $t("delete logs") }}</span>
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
        longDescription?: string;
        breadcrumb?: {
            label: string;
            link?: RouterLinkTo;
            disabled?: boolean;
        }[];
        beta?: boolean;
    }>();

    const route = useRoute();
    const logsStore = useLogsStore();
    const flowStore = useFlowStore();
    const layoutStore = useLayoutStore();
    const bookmarksStore = useBookmarksStore();


    const shouldDisplayDeleteButton = computed(() => {
        return route.name === "flows/update" && route.params?.tab === "logs";
    });

    const bookmarked = computed(() => {
        return bookmarksStore.pages.some((page) => page.path === currentFavURI.value);
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

    const toast = useToast();
    const {t} = useI18n();

    const deleteLogs = () => {
        if(!flowStore.flow){
            throw new Error("No flow selected");
        }
        toast.confirm(
            t("delete_all_logs"),
            async () => {
                if(!flowStore.flow){
                    return;
                }
                return logsStore.deleteLogs({
                    namespace: flowStore.flow?.namespace,
                    flowId: flowStore.flow?.id
                })
            },
        );
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
    @import "@kestra-io/ui-libs/src/scss/color-palette.scss";

    nav {
        top: 0;
        position: sticky;
        z-index: 1000;
        padding: 1rem 2rem;
        border-bottom: 1px solid var(--ks-border-primary);
        background: var(--ks-background-card);

        .top-title, h1, .el-breadcrumb {
            white-space: nowrap;
            max-width: 100%;
            text-overflow: ellipsis;
            overflow: hidden;
        }

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

        .description {
            font-size: 0.875rem;
            margin-top: -0.5rem;
            color: var(--ks-content-secondary);
        }

        .icon {
            border: none;
            color: var(--ks-content-tertiary);

            &:deep(svg) {
                fill: currentColor;
                stroke: currentColor;
            }

            &.active {
                color: $base-purple-300;
            }
        }

        :deep(.el-breadcrumb__item) {
            display: inline-block;
        }

        :deep(.el-breadcrumb__inner) {
            white-space: nowrap;
            max-width: 100%;
            text-overflow: ellipsis;
            overflow: hidden;
        }

        .side {
            .fixed-buttons {
                align-items: center;

                button, :deep(button), a, :deep(a) {
                    border: none;
                    font-size: var(--font-size-lg);
                    padding: .25rem;
                }
            }

            :slotted(ul), :deep(ul) {
                display: flex;
                list-style: none;
                padding: 0;
                margin: 0;
                gap: .5rem;
                align-items: center;
            }
        }

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
</style>
