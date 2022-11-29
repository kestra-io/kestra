<template>
    <el-dropdown-item
        :icon="icon.ChartAreaspline"
        :disabled="!(metrics && metrics.length > 0)"
        @click="isOpen = !isOpen"
    >
        {{ $t('metrics') }}
    </el-dropdown-item>

    <el-drawer
        v-if="isOpen"
        v-model="isOpen"
        :title="$t('metrics')"
        destroy-on-close
        :append-to-body="true"
        size="50%"
        direction="ltr"
    >
        <el-table
            :data="metrics"
            ref="table"
            :default-sort="{prop: 'name', order: 'ascending'}"
            stripe
            table-layout="auto"
            fixed
        >
            <el-table-column prop="name" sortable :label="$t('name')">
                <template #default="scope">
                    <template v-if="scope.row.type === 'timer'">
                        <kicon><timer /></kicon>
                    </template>
                    <template v-else>
                        <kicon><counter /></kicon>
                    </template>
                    &nbsp;<code>{{ scope.row.name }}</code>
                </template>
            </el-table-column>

            <el-table-column prop="tags" sortable :label="$t('tags')">
                <template #default="scope">
                    <el-tag
                        v-for="(value, key) in scope.row.tags"
                        :key="key"
                        class="mr-1"
                        type="info"
                        size="small"
                        disable-transitions
                    >
                        {{ key }}: <strong>{{ value }}</strong>
                    </el-tag>
                </template>
            </el-table-column>

            <el-table-column prop="value" sortable :label="$t('value')">
                <template #default="scope">
                <span v-if="scope.row.type === 'timer'">
                    {{ $filters.humanizeDuration(scope.row.value) }}
                </span>
                    <span v-else>
                    {{ $filters.humanizeNumber(scope.row.value) }}
                </span>
                </template>
            </el-table-column>
        </el-table>
    </el-drawer>
</template>

<script>
    import {shallowRef} from "vue";
    import Kicon from "../Kicon";
    import Timer from "vue-material-design-icons/Timer";
    import Counter from "vue-material-design-icons/Numeric";
    import ChartAreaspline from "vue-material-design-icons/ChartAreaspline.vue";

    export default {
        components: {
            Kicon,
            Timer,
            Counter,
        },
        props: {
            metrics: {
                type: Array,
                required: true
            }
        },
        data() {
            return {
                isOpen: false,
                icon: {ChartAreaspline: shallowRef(ChartAreaspline)}
            };
        },
    };
</script>
