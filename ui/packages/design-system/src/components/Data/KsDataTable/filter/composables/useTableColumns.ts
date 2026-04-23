import {ref, computed, onMounted} from "vue";
import {useLocalStorage} from "@vueuse/core";

export interface ColumnConfig {
    label: string;
    prop: string;
    default: boolean;
    description?: string;
    condition?: () => boolean;
}

export interface UseTableColumnsOptions {
    columns: ColumnConfig[];
    storageKey: string;
    initialVisibleColumns?: string[];
}

export function useTableColumns({columns, storageKey, initialVisibleColumns = []}: UseTableColumnsOptions) {
    const orderStorageKey = `ks-column-order-${storageKey}`;
    const visibilityStorageKey = `columns_${storageKey}`;
    const defaultOrder = columns.map(c => c.prop);

    const columnOrder = useLocalStorage<string[]>(
        orderStorageKey,
        defaultOrder,
        {
            serializer: {
                read: (v: string) => {
                    try {
                        const parsed = JSON.parse(v);
                        const isValid = parsed.length === defaultOrder.length &&
                            parsed.every((p: string) => defaultOrder.includes(p));
                        return isValid ? parsed : defaultOrder;
                    } catch {
                        return defaultOrder;
                    }
                },
                write: (v: string[]) => JSON.stringify(v)
            }
        }
    );

    const visibleColumns = ref<string[]>([]);

    const orderedColumns = computed(() =>
        columnOrder.value.map(p => columns.find(c => c.prop === p)).filter(Boolean) as ColumnConfig[]
    );

    const orderedVisibleColumns = computed(() =>
        columnOrder.value.filter(p => visibleColumns.value.includes(p))
    );

    const visibleCount = computed(() => visibleColumns.value.length);
    const totalCount = computed(() => columns.length);

    const initializeVisibleColumns = () => {
        const stored = localStorage.getItem(visibilityStorageKey);
        if (stored) {
            try {
                const parsed = stored.split(",");
                const valid = parsed.filter(p => columns.some(c => c.prop === p));
                if (valid.length) {
                    visibleColumns.value = valid;
                    return;
                }
            } catch { // ignore
            } 
        }
        visibleColumns.value = initialVisibleColumns.length
            ? initialVisibleColumns
            : columns.filter(c => c.default && (!c.condition || c.condition())).map(c => c.prop);
    };

    const isVisible = (column: ColumnConfig) => visibleColumns.value.includes(column.prop);

    const toggleColumn = (column: ColumnConfig) => {
        const prop = column.prop;
        if (isVisible(column)) {
            visibleColumns.value = visibleColumns.value.filter(p => p !== prop);
        } else {
            const currentOrdered = orderedVisibleColumns.value;
            const propIndex = columnOrder.value.indexOf(prop);
            const visibleBefore = currentOrdered.filter(p =>
                columnOrder.value.indexOf(p) < propIndex
            ).length;
            const insertIndex = visibleBefore;
            visibleColumns.value = [
                ...currentOrdered.slice(0, insertIndex),
                prop,
                ...currentOrdered.slice(insertIndex)
            ];
        }
        localStorage.setItem(visibilityStorageKey, visibleColumns.value.join(","));
    };

    const reorderColumns = (fromIndex: number, toIndex: number) => {
        if (fromIndex === toIndex) return;
        const newOrder = [...columnOrder.value];
        const [dragged] = newOrder.splice(fromIndex, 1);
        newOrder.splice(toIndex, 0, dragged);
        columnOrder.value = newOrder;
        visibleColumns.value = orderedVisibleColumns.value;
        localStorage.setItem(visibilityStorageKey, visibleColumns.value.join(","));
    };

    const updateVisibleColumns = (newColumns: string[]) => {
        visibleColumns.value = newColumns;
        localStorage.setItem(visibilityStorageKey, newColumns.join(","));
    };

    onMounted(initializeVisibleColumns);

    return {
        visibleColumns,
        orderedColumns,
        orderedVisibleColumns,
        visibleCount,
        totalCount,
        isVisible,
        toggleColumn,
        reorderColumns,
        updateVisibleColumns,
        initializeVisibleColumns,
    };
}