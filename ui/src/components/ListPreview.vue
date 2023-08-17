<template>
    <el-table :data="value" stripe>
        <el-table-column v-for="(column, index) in generateTableColumns" :key="index" :prop="column" :label="column" />
    </el-table>
</template>
<script>

    export default {
        name: "ListPreview",
        props: {
            value: {
                type: Array,
                required: true
            }
        },
        computed: {
            generateTableColumns() {
                return Object.keys(this.value[0]);
            }
        }
    }
</script>

<style scoped lang="scss">
</style>