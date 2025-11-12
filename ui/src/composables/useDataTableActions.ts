import {ref, computed, watch, onMounted, type Ref} from "vue";
import {useRoute, useRouter} from "vue-router";
import _merge from "lodash/merge";
import _cloneDeep from "lodash/cloneDeep";
import _isEqual from "lodash/isEqual";
import useRestoreUrl from "./useRestoreUrl";

interface SortItem {
    prop?: string;
    order?: "ascending" | "descending";
}

interface PageChangeItem {
    size: number;
    page: number;
}

export interface DataTableRef {
    isLoading: boolean;
}

interface DataTableActionsOptions {
    filters?: Record<string, any>;
    pageSize?: number;
    pageNumber?: number;
    dblClickRouteName?: string;
    embed?: boolean;
    dataTableRef?: Ref<DataTableRef | null>;
    loadData?: (callback?: () => void) => void;
}

export function useDataTableActions(options: DataTableActionsOptions = {}) {
    const route = useRoute();
    const router = useRouter();

    const sort = ref("");
    const dblClickRouteName = ref(options.dblClickRouteName);
    const ready = ref(false);
    const internalPageSize = ref(25);
    const internalPageNumber = ref(1);
    const internalSort = ref<string>();

    const filters = computed(() => options.filters || {});
    const pageSize = computed(() => options.pageSize);
    const pageNumber = computed(() => options.pageNumber);
    const embed = computed(() => options.embed);
    const dataTableRef = computed(() => options.dataTableRef?.value);

    const {loadInit, saveRestoreUrl} = useRestoreUrl({restoreUrl: true});

    const sortString = (sortItem: SortItem, sortKeyMapper: (k: string) => string): string | undefined => {
        if (sortItem && sortItem.prop && sortItem.order) {
            return `${sortKeyMapper(sortItem.prop)}:${sortItem.order === "descending" ? "desc" : "asc"}`;
        }
    };

    const onSort = (sortItem: SortItem, sortKeyMapper = (k: string) => k) => {
        internalSort.value = sortString(sortItem, sortKeyMapper);

        if (internalSort.value) {
            const sort = internalSort.value;
            router.push({
                query: {...route.query, sort}
            });
        } else {
            load(onDataLoaded);
        }
    };

    const onRowDoubleClick = (item: Record<string, any>) => {
        router.push({
            name: dblClickRouteName.value || route.name?.toString().replace("/list", "/update"),
            params: {
                ...item,
                tenant: route.params.tenant
            }
        });
    };

    const onDataTableValue = (keyOrObject: string | Record<string, any>, value?: any) => {
        const values = typeof keyOrObject === "string" ? {[keyOrObject]: value} : keyOrObject;
        const query = {...route.query};

        for (const [key, value] of Object.entries(values)) {
            if (value === undefined || value === "" || value === null || 
                (Array.isArray(value) && value.length === 0)) {
                delete query[key];
            } else {
                query[key] = value;
            }
        }

        internalPageNumber.value = 1;

        router.push({query});
    };

    const onPageChanged = (item: PageChangeItem) => {
        if (internalPageSize.value === item.size && internalPageNumber.value === item.page) return;

        internalPageSize.value = item.size;
        internalPageNumber.value = item.page;

        if (!embed.value) {
            const {size: _size, page: _page, ...otherQuery} = route.query;
            router.push({
                query: {
                    size: item.size,
                    page: item.page,
                    ...otherQuery
                }
            });
        } else {
            load(onDataLoaded);
        }
    };

    const queryWithFilter = (namespace?: string, excludedKeys: string[] = []): Record<string, any> => {
        let query = route.query;

        if (namespace !== undefined) {
            query = Object.fromEntries(
                Object.entries(query)
                    .filter(([key]) => key.startsWith(`${namespace}[`))
                    .map(([key, value]) => [key.substring(namespace.length + 2, key.length - 1), value])
            );
        }

        if (excludedKeys.length > 0) {
            const filterKeyMatcher = new RegExp(`^(?:filters\\[)?(?:${excludedKeys.join(")|(?:")})`);
            query = Object.fromEntries(
                Object.entries(query).filter(([key]) => filterKeyMatcher.exec(key) === null)
            );
        }

        return _merge(_cloneDeep(query), filters.value || {});
    };

    const load = (callback?: () => void) => {
        if (dataTableRef.value) {
            dataTableRef.value.isLoading = true;
        }

        if (options.loadData) {
            options.loadData(callback || onDataLoaded);
        }
    };

    const onDataLoaded = () => {
        ready.value = true;
        loadInit.value = true;

        saveRestoreUrl();

        if (dataTableRef.value) {
            dataTableRef.value.isLoading = false;
        }
    };

    const refreshPaging = () => {
        internalPageSize.value = pageSize.value ?? Number(route.query.size ?? 25);
        internalPageNumber.value = pageNumber.value ?? Number(route.query.page ?? 1);
    };

    watch(
        () => route.query,
        (newQuery, oldQuery) => {
            if (!_isEqual(newQuery, oldQuery)) {
                refreshPaging();
                load(onDataLoaded);
            }
        },
        {deep: true}
    );

    onMounted(() => {
        refreshPaging();

        // @TODO: ugly hack from restoreUrl
        if (loadInit.value) {
            load(onDataLoaded);
        }
    });

    return {
        sort,
        dblClickRouteName,
        loadInit,
        ready,
        internalPageSize,
        internalPageNumber,
        internalSort,
        filters,
        pageSize,
        pageNumber,
        embed,
        sortString,
        onSort,
        onRowDoubleClick,
        onDataTableValue,
        onPageChanged,
        queryWithFilter,
        load,
        onDataLoaded,
        refreshPaging
    };
}

