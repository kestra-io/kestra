<template>
    <b-card v-if="node" :title="`${this.$t('task')} → ${task.id}`">
        <b-card-text>
            <b-table class="table-sm" striped hover :items="items">
                <template v-slot:cell(value)="row">
                    <div v-html="row.item.value" />
                </template>
            </b-table>
        </b-card-text>
    </b-card>
</template>
<script>
import Yaml from "yaml";
import { mapState } from "vuex";
export default {
    computed: {
        ...mapState("graph", ["node"]),
        task() {
            return this.node && this.node.task;
        },
        items() {
            const items = [];
            for (const property in this.task) {
                const v = this.task[property];
                const value =
                    typeof v === "object"
                        ? `<pre>${Yaml.stringify(v)}</pre>`
                        : v;
                items.push({ property, value });
            }
            return items;
        }
    }
};
</script>
