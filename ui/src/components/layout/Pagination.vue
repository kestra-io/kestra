<template>
    <div class="d-flex pagination" :class="{'top': top}">
        <slot name="search" />
        <div class="flex-grow-1 d-sm-none d-md-inline-block page-size">
            <el-select
                v-if="!top"
                size="small"
                :modelValue="internalSize"
                @update:model-value="pageSizeChange"
            >
                <el-option
                    v-for="item in pageOptions"
                    :key="item.value"
                    :label="item.text"
                    :value="item.value"
                />
            </el-select>
        </div>
        <div v-if="isPaginationDisplayed">
            <el-pagination
                v-model:currentPage="internalPage"
                v-model:pageSize="internalSize"
                size="small"
                layout="prev, pager, next"
                :pagerCount="5"
                :total="total"
                @current-change="pageChanged"
                class="my-0"
            />
        </div>

        <small class="total ms-2">
            {{ $t('Total') }}: {{ total }}
        </small>
    </div>
</template>
<script setup lang="ts">
    import {ref, computed, watch} from "vue";
    import {useI18n} from "vue-i18n";
    import {useRoute} from "vue-router";
    import {storageKeys} from "../../utils/constants";

    const props = defineProps<{
        total?: number;
        size?: number;
        page?: number;
        top?: boolean;
    }>();

    const emit = defineEmits<{
        (e: "page-changed", payload: { page?: number; size?: number }): void;
    }>();

    const route = useRoute();
    const PAGINATION_SIZE = `${storageKeys.PAGINATION_SIZE}__${String(route.name)}`;

    const {t} = useI18n();

    const pageOptions = [
        {value: 10, text: `10 ${t("Per page")}`},
        {value: 25, text: `25 ${t("Per page")}`},
        {value: 50, text: `50 ${t("Per page")}`},
        {value: 100, text: `100 ${t("Per page")}`},
    ];

    const internalSize = ref<number>(
        parseInt(
            localStorage.getItem(PAGINATION_SIZE) as string ||
                (route.query.size as string) ||
                props.size?.toString() ||
                "25"
        )
    );

    const internalPage = ref<number>(
        parseInt((route.query.page as string) || props.page?.toString() || "1")
    );

    emit("page-changed", {
        page: internalPage.value,
        size: internalSize.value,
    });

    function pageSizeChange(value: number) {
        internalPage.value = 1;
        internalSize.value = value;
        localStorage.setItem(PAGINATION_SIZE, value.toString());
        emit("page-changed", {
            page: 1,
            size: internalSize.value,
        });
    }

    function pageChanged(page: number) {
        internalPage.value = page;
        emit("page-changed", {
            page: page,
            size: internalSize.value,
        });
    }

    const isPaginationDisplayed = computed(() => {
        if (internalPage.value === 1 && (props.total ?? 0) < internalSize.value) {
            return false;
        }
        return true;
    });

    watch(
        () => route.query,
        () => {
            internalSize.value = parseInt(
                localStorage.getItem(PAGINATION_SIZE) as string ||
                    (route.query.size as string) ||
                    props.size?.toString() ||
                    "25"
            );
            internalPage.value = parseInt((route.query.page as string) || props.page?.toString() || "1");
        },
        {immediate: true}
    );
    
    // Watch for prop changes to keep pagination controls synchronized
    watch(() => props.page, (newPage) => {
        internalPage.value = newPage ?? 1;
    });

    watch(() => props.size, (newSize) => {
        internalSize.value = newSize ?? 25;
    });
</script>
<style scoped lang="scss">
    @use 'element-plus/theme-chalk/src/mixins/mixins' as *;

    .pagination {
        margin-top: 1rem;

        &.top {
            margin-bottom: 1rem;
            margin-top: 0;
        }

        .el-select {
            width: 105px;
        }

        .page-size {
            @include res(xs) {
                display: none;
            }
        }

        .total {
            padding: 0 4px;
            line-height: 1.85;
            font-size: var(--el-font-size-extra-small);
            color: var(--ks-content-secondary);
            white-space: nowrap;
        }

        :deep(.el-pagination .el-pager li) {
            background-color: var(--ks-button-background-secondary);
            border: 1px solid var(--ks-border-primary);
            color: var(--ks-content-primary);

            &:hover, &.is-active {
                background-color: var(--ks-button-background-secondary-hover);
                border: 1px solid var(--ks-border-active);
            }
        }
    }
</style>