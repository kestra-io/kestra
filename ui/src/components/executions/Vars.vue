<template>
    <el-table stripe table-layout="auto" fixed :data="variables">
        <el-table-column prop="key" min-width="500" :label="$t(keyLabelTranslationKey)">
            <template #default="scope">
                <code class="key-col">{{ scope.row.key }}</code>
            </template>
        </el-table-column>

        <el-table-column prop="value" :label="$t('value')">
            <template #default="scope">
                <template v-if="scope.row.date">
                    <date-ago :inverted="true" :date="scope.row.value" />
                </template>
                <template v-else-if="scope.row.subflow">
                    {{ scope.row.value }}
                    <sub-flow-link :execution-id="scope.row.value" />
                </template>
                <template v-else>
                    <var-value :execution="execution" :value="scope.row.value" />
                </template>
            </template>
        </el-table-column>
    </el-table>
</template>

<script>
    import Utils from "../../utils/utils";
    import VarValue from "./VarValue.vue";
    import DateAgo from "../../components/layout/DateAgo.vue";
    import SubFlowLink from "../flows/SubFlowLink.vue"
    import {mapState} from "vuex";

    export default {
        components: {
            DateAgo,
            VarValue,
            SubFlowLink
        },
        props: {
            data: {
                type: Object,
                required: true
            },
            keyLabelTranslationKey: {
                type: String,
                required: false,
                default: "name"
            }
        },
        computed: {
            ...mapState("execution", ["execution"]),
            variables() {
                return Utils.executionVars(this.data);
            },
        },
    };
</script>
<style>
    .key-col {
        min-width: 200px;
    }
</style>