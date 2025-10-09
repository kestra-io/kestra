import {ref, computed, onMounted} from "vue";

export interface ColumnConfig {
    label: string;
    prop: string;
    default: boolean;
    condition?: () => boolean;
}

export interface UseTableColumnsOptions {
    columns: ColumnConfig[];
    storageKey: string;
    initialVisibleColumns?: string[];
}

export function useTableColumns({columns, storageKey, initialVisibleColumns = []}: UseTableColumnsOptions) {
    const visibilityStorageKey = `columns_${storageKey}`;

    const visibleColumns = ref<string[]>([]);

    const visibleCount = computed(() => visibleColumns.value.length);
    const totalCount = computed(() => columns.length);

    const initializeVisibleColumns = () => {
        const stored = localStorage.getItem(visibilityStorageKey);
        if (stored) {
            try {
                const parsed = stored.split(",");
                const validColumns = parsed.filter(prop =>
                    columns.some(column => column.prop === prop)
                );
                if (validColumns.length > 0) {
                    visibleColumns.value = validColumns;
                    return;
                }
            } catch {
                // ignore
            }
        }

        visibleColumns.value = initialVisibleColumns.length > 0
            ? initialVisibleColumns
            : columns.filter(col => col.default && (!col.condition || col.condition())).map(col => col.prop);
    };

    const isVisible = (column: ColumnConfig): boolean => {
        return visibleColumns.value.includes(column.prop);
    };

    const toggleColumn = (column: ColumnConfig) => {
        let newVisibleColumns: string[];

        if (isVisible(column)) {
            newVisibleColumns = visibleColumns.value.filter(prop => prop !== column.prop);
        } else {
            newVisibleColumns = [...visibleColumns.value, column.prop];
        }

        visibleColumns.value = newVisibleColumns;
        localStorage.setItem(visibilityStorageKey, newVisibleColumns.join(","));
    };

    const updateVisibleColumns = (newColumns: string[]) => {
        visibleColumns.value = newColumns;
        localStorage.setItem(visibilityStorageKey, newColumns.join(","));
    };

    onMounted(() => {
        initializeVisibleColumns();
    });

    return {
        visibleColumns,
        visibleCount,
        totalCount,
        isVisible,
        toggleColumn,
        updateVisibleColumns,
        initializeVisibleColumns
    };
}