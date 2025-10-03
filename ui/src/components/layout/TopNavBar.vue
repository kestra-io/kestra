<template>
    <nav data-component="TopBarNav" class="d-flex w-100 gap-3 top-bar">
        <div class="d-flex flex-grow-1 flex-shrink-1 overflow-hidden top-title align-items-center gap-2">
            <SidebarToggleButton 
                v-if="layoutStore.sideMenuCollapsed"
                @toggle="layoutStore.setSideMenuCollapsed(false)" 
            />
            <div class="breadcrumb-container d-flex align-items-center">
                <template v-if="breadcrumb && breadcrumb.length > 0">
                    <template v-if="breadcrumb.length <= 2">
                        <template v-for="(item, x) in breadcrumb" :key="x">
                            <router-link 
                                v-if="!item.disabled && item.link" 
                                :to="item.link" 
                                class="breadcrumb-link"
                            >
                                {{ item.label }}
                            </router-link>
                            <span 
                                v-else 
                                class="breadcrumb-link" 
                                :class="{'disabled': item.disabled}"
                            >
                                {{ item.label }}
                            </span>
                            <span class="breadcrumb-separator">/</span>
                        </template>
                    </template>
                    <template v-else>
                        <router-link 
                            v-if="!breadcrumb[0].disabled && breadcrumb[0].link" 
                            :to="breadcrumb[0].link" 
                            class="breadcrumb-link"
                        >
                            {{ breadcrumb[0].label }}
                        </router-link>
                        <span v-else class="breadcrumb-link">
                            {{ breadcrumb[0].label }}
                        </span>
                        <span class="breadcrumb-separator">/</span>
                        <span class="breadcrumb-ellipsis">...</span>
                        <span class="breadcrumb-separator">/</span>
                        <router-link 
                            v-if="!breadcrumb[breadcrumb.length - 1].disabled && breadcrumb[breadcrumb.length - 1].link" 
                            :to="breadcrumb[breadcrumb.length - 1].link" 
                            class="breadcrumb-link"
                        >
                            {{ breadcrumb[breadcrumb.length - 1].label }}
                        </router-link>
                        <span v-else class="breadcrumb-link">
                            {{ breadcrumb[breadcrumb.length - 1].label }}
                        </span>
                        <span class="breadcrumb-separator">/</span>
                    </template>
                </template>
                <h1 class="h5 fw-semibold m-0 d-inline-flex align-items-center">
                    <slot name="title">
                        {{ title }}
                        <el-tooltip v-if="description" :content="description">
                            <Information class="ms-2" />
                        </el-tooltip>
                        <Badge v-if="beta" label="Beta" />
                    </slot>
                    <el-button
                        class="star-button"
                        :class="{'star-active': bookmarked}"
                        :icon="bookmarked ? StarIcon : StarOutlineIcon"
                        circle
                        @click="onStarClick"
                    />
                </h1>
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
    import {useRoute} from "vue-router";
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

    const props = defineProps<{
        title: string;
        description?: string;
        breadcrumb?: { label: string; link?: string; disabled?: boolean }[];
        beta?: boolean;
    }>();

    const logsStore = useLogsStore();
    const bookmarksStore = useBookmarksStore();
    const flowStore = useFlowStore();
    const route = useRoute();
    const layoutStore = useLayoutStore();

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
        if (!flowStore.flow) {
            throw new Error("No flow selected");
        }
    
        toast.confirm(
            t("delete_all_logs"),
            async () => {
                if (!flowStore.flow) {
                    return;
                }
                return logsStore.deleteLogs({
                    namespace: flowStore.flow.namespace,
                    flowId: flowStore.flow.id
                });
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

<style lang="scss" scoped>
nav {
    top: 0;
    position: sticky;
    z-index: 1000;
    padding: 1rem 2rem;
    border-bottom: 1px solid var(--ks-border-primary);
    background: var(--ks-background-card);

    .top-title {
        white-space: nowrap;
        max-width: 100%;
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

        &:hover:not(.disabled) {
            color: var(--ks-text-primary);
        }

        &.disabled {
            cursor: default;
            color: var(--ks-text-secondary, #999);
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
    }

    h1 {
        line-height: 1.6;
        display: flex !important;
        align-items: center;
        white-space: nowrap;
        flex-shrink: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        min-width: 0;
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
</style>