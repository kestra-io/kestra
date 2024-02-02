<template>
    <el-table
        v-loading="flowData === undefined"
        :data="flowData ? flowData.triggers : []"
        stripe
        table-layout="auto"
        @row-dblclick="triggerId = $event.id; isOpen = true"
    >
        <el-table-column prop="id" :label="$t('id')">
            <template #default="scope">
                <code>
                    {{ scope.row.id }}
                </code>
            </template>
        </el-table-column>
        <el-table-column prop="type" :label="$t('type')" />
        <el-table-column :label="$t('description')">
            <template #default="scope">
                <Markdown :source="scope.row.description" />
            </template>
        </el-table-column>

        <el-table-column column-key="action" class-name="row-action">
            <template #default="scope">
                <a href="#" @click="triggerId = scope.row.id; isOpen = true">
                    <kicon :tooltip="$t('details')" placement="left">
                        <TextSearch />
                    </kicon>
                </a>
            </template>
        </el-table-column>
    </el-table>

    <el-drawer
        v-if="isOpen"
        v-model="isOpen"
        destroy-on-close
        lock-scroll
        size=""
        :append-to-body="true"
    >
        <template #header>
            <code>{{ triggerId }}</code>
        </template>
        <el-table stripe table-layout="auto" :data="triggerData">
            <el-table-column prop="key" :label="$t('key')" />
            <el-table-column prop="value" :label="$t('value')">
                <template #default="scope">
                    <vars v-if="scope.row.value instanceof Array || scope.row.value instanceof Object " :data="scope.row.value" />
                </template>
            </el-table-column>
        </el-table>
    </el-drawer>
</template>

<script setup>
    import TextSearch from "vue-material-design-icons/TextSearch.vue";
    import Vars from "../executions/Vars.vue";
</script>

<script>
    import Markdown from "../layout/Markdown.vue";
    import {mapGetters} from "vuex";
    import Kicon from "../Kicon.vue"
    import {apiUrl} from "override/utils/route";

    export default {
        components: {Markdown, Kicon},
        data() {
            return {
                triggerId: undefined,
                flowData: undefined,
                isOpen: false
            }
        },
        created() {
            this.$http
                .get(`${apiUrl(this.$store)}/flows/${this.flow.namespace}/${this.flow.id}?source=false`)
                .then(value => this.flowData = value.data);
        },
        computed: {
            ...mapGetters("flow", ["flow"]),
            triggerData() {
                return Object
                    .entries(this.flowData.triggers.filter(trigger => trigger.id === this.triggerId)[0])
                    .map(([key, value]) => {
                        return {
                            key,
                            value
                        };
                    });
            },
        }
    };
</script>