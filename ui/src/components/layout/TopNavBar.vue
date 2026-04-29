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

<style lang="scss" scoped>
    .beta-badge {
        border-radius: calc(var(--kel-border-radius-round) * 2);
    }
</style>
