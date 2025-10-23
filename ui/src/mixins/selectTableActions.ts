import {defineComponent} from "vue";

export default defineComponent({
    data() {
        return {
            queryBulkAction: false,
            selection: [] as any[],
        };
    },
    methods: {
        handleSelectionChange(value: any[]) {
            this.selection = value.map(this.selectionMapper);

            if (this.queryBulkAction && this.elTable && value?.length < this.elTable.data?.length) {
                this.queryBulkAction = false
            }
        },
        toggleAllUnselected() {
            this.elTable.clearSelection()
            this.queryBulkAction = false;
        },
        toggleAllSelection() {
            if (this.elTable.getSelectionRows().length < this.elTable.data.length) {
                this.elTable.toggleAllSelection()
            }
            this.queryBulkAction = true;
        },
        selectionMapper(element: any) {
            return element;
        }
    },
    computed: {
        elTable() {
            return (this.$refs.selectTable as any).$refs.table;
        }
    }
})
