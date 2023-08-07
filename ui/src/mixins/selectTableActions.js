export default {
    data() {
        return {
            queryBulkAction: false,
            selection: []
        };
    },
    methods: {
        handleSelectionChange(val) {
            if (val.length === this.total) {
                this.queryBulkAction = true
            } else if (val.length < this.internalPageSize) {
                this.queryBulkAction = false
            }
            this.selection = val.map(this.selectionMapper);
        },
        toggleAllSelection() {
            // only some are selected, we should set queryBulkAction to true because it will select all
            if (this.elTable.getSelectionRows().length > 0 && !this.queryBulkAction) {
                this.queryBulkAction = true;
            }
            this.elTable.toggleAllSelection();
        },
        selectionMapper(element) {
            return element;
        }
    },
    computed: {
        elTable() {
            return this.$refs.selectTable.$refs.table;
        }
    }
}
