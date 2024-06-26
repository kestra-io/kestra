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
