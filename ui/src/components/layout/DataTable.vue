<template>
    <div>
        <nav v-if="hasNavBar">
            <Collapse>
                <slot name="navbar" />
            </Collapse>
        </nav>

        <el-container direction="vertical" v-loading="isLoading">
            <slot name="top" />

            <Pagination v-if="!embed && !hideTopPagination" :size="size" :top="true" :page="page" :total="total" @page-changed="onPageChanged">
                <template #search>
                    <slot name="search" />
                </template>
            </Pagination>

            <slot name="table" />

            <Pagination v-if="total > 0" :size="size" :page="page" :total="total" @page-changed="onPageChanged" />
        </el-container>
    </div>
</template>

<script lang="ts" setup>
    import {ref, computed, useSlots} from "vue";
    import Pagination from "./Pagination.vue";
    import Collapse from "./Collapse.vue";

    defineProps<{
        total: number;
        size?: number;
        page?: number;
        embed?: boolean;
        hideTopPagination?: boolean;
    }>();

    const emit = defineEmits<{
        (e: "page-changed", pagination: any): void;
    }>();

    const slots = useSlots();

    const isLoading = ref(false);

    const hasNavBar = computed(() => !!slots["navbar"]);

    function onPageChanged(pagination: any) {
        emit("page-changed", pagination);
    }
</script>
