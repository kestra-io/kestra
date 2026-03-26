<template>
    <ks-top-nav-bar
        :title="title"
        :description="description"
        :longDescription="longDescription"
        :breadcrumb="breadcrumb"
        :beta="beta"
        :isBookmarked="bookmarked"
        @star-click="onStarClick"
    >
        <template v-if="layoutStore.sideMenuCollapsed" #sidebar-toggle>
            <SidebarToggleButton @toggle="layoutStore.setSideMenuCollapsed(false)" />
        </template>
        <template v-if="$slots.title" #title>
            <slot name="title" />
        </template>
        <template v-if="$slots.description" #description>
            <slot name="description" />
        </template>
        <template #search>
            <GlobalSearch class="trigger-flow-guided-step" />
        </template>
        <template v-if="shouldDisplayDeleteButton && logsStore.logs !== undefined && logsStore.logs.length > 0" #pre-action>
            <ks-button @click="deleteLogs()">
                <TrashCan class="me-2" />
                <span>{{ $t("delete logs") }}</span>
            </ks-button>
        </template>
        <template v-if="$slots['more-actions']" #more-actions>
            <slot name="more-actions" />
        </template>
        <template v-if="$slots['actions']" #actions>
            <slot name="actions" />
        </template>
        <template #badge v-if="beta || true">
            <ks-button type="primary" size="small" class="beta-badge" round>
                Beta
            </ks-button>
        </template>
    </ks-top-nav-bar>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";
    import {useRoute, RouterLink} from "vue-router";
    import GlobalSearch from "./GlobalSearch.vue";
    import TrashCan from "vue-material-design-icons/TrashCan.vue";
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

<style lang="scss" scoped>
    .beta-badge {
        border-radius: calc(var(--kel-border-radius-round) * 2);
    }
</style>
