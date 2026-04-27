<template>
    <nav class="d-flex align-items-center w-100 top-bar">
        <SidebarToggleButton
            v-if="layoutStore.sideMenuCollapsed"
            @toggle="layoutStore.setSideMenuCollapsed(false)"
        />
        <div class="title-section">
            <div class="d-flex align-items-center gap-2">
                <Breadcrumb :items="breadcrumbItems" :title="title">
                    <template v-if="$slots.title" #title>
                        <slot name="title" />
                    </template>
                </Breadcrumb>
                <el-tooltip v-if="description" :content="description">
                    <Information class="ms-2 icon" />
                </el-tooltip>
                <Badge v-if="beta" label="Beta" />
                <el-button
                    class="icon"
                    :class="{'active': bookmarked}"
                    :icon="bookmarked ? StarIcon : StarOutlineIcon"
                    circle
                    @click="onStarClick"
                />
            </div>
            <div v-if="longDescription || $slots.description" class="description">
                <slot name="description">
                    {{ longDescription }}
                </slot>
            </div>
        </div>
        <div class="d-flex side gap-2 flex-shrink-0 align-items-center">
            <GlobalSearch class="trigger-flow-guided-step" />
            <slot name="additional-right" />
        </div>
    </nav>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";
    import {useRoute} from "vue-router";
    import GlobalSearch from "./GlobalSearch.vue";
    import StarOutlineIcon from "vue-material-design-icons/StarOutline.vue";
    import StarIcon from "vue-material-design-icons/Star.vue";
    import Information from "vue-material-design-icons/Information.vue";
    import Badge from "../global/Badge.vue";
    import {useBookmarksStore} from "../../stores/bookmarks";
    import {useLayoutStore} from "../../stores/layout";
    import SidebarToggleButton from "./SidebarToggleButton.vue";
    import Breadcrumb from "./Breadcrumb.vue";
    import type {BreadcrumbItem} from "./breadcrumbTypes";

    const props = defineProps<{
        title: string;
        description?: string;
        longDescription?: string;
        breadcrumb?: BreadcrumbItem[];
        beta?: boolean;
    }>();

    const route = useRoute();
    const layoutStore = useLayoutStore();
    const bookmarksStore = useBookmarksStore();

    const breadcrumbItems = computed(() => [
        {label: t("home"), link: {name: "home"}},
        ...(props.breadcrumb ?? []),
    ]);

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

    const {t} = useI18n();

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
        padding: 0.5rem 1rem;
        gap: 1rem;
        border-bottom: 1px solid var(--ks-border-primary);
        background: var(--ks-background-card);

        .title-section {
            display: flex;
            flex-direction: column;
            align-items: flex-start;
            gap: 0.5rem;
            flex: 1 0 0;
            min-width: 0;
            overflow: hidden;
            mask-image: linear-gradient(to right, black calc(100% - 40px), transparent 100%);
        }

        .description {
            font-size: var(--font-size-sm);
            color: var(--ks-content-secondary);
            white-space: nowrap;
        }

        .icon {
            border: none;
            color: var(--ks-content-tertiary);
            flex-shrink: 0;

            &:deep(svg) {
                fill: currentColor;
                stroke: currentColor;
            }

            &.active {
                color: $base-purple-300;
            }
        }

        .side {
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
            padding: 0.5rem 0.75rem;
        }

        @media (max-width: 768px) {
            padding: 0.5rem;
        }
    }
</style>
