<template>
    <el-table :data="data" stripe>
        <el-table-column v-for="(column, index) in generateTableColumns" :key="index" :prop="column.props.prop" :label="column.label">
            <template #default="scope">
                <span class="">{{ scope.row[index] }}</span>
            </template>
        </el-table-column>
    </el-table>
</template>
<script>

    export default {
        name: "Csv",
        props: {
            value: {
                type: Object,
                required: true
            }
        },
        computed: {
            generateTableColumns() {
                const columns = [];
                for (let i = 0; i < this.value.data[0].length; i++) {
                    columns.push({
                        props: {prop: String(i)},
                        label: this.value.data[0][i],
                    });
                }

                return columns;
            },
            data() {
                return this.value.data.slice(1, this.value.data.length);
            }
        }
    }
</script>

<style scoped lang="scss">
</style>