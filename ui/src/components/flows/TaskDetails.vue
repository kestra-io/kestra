<template>
    <b-card v-if="task" :title="`${this.$t('task').capitalize()} → ${task.id}`" >
        <b-card-text>
            <b-table class="table-sm" striped hover :items="items">
                <template v-slot:cell(value)="row">
                    <div v-html="row.item.value"/>
                </template>
            </b-table>
        </b-card-text>
    </b-card>
</template>
<script>
import Yaml from "yaml";
export default {
    props: {
        task: {
            type: Object,
            required: false
        }
    },
    computed: {
        items() {
            const items = [];
            for (const property in this.task) {
                const v = this.task[property];
                const value = typeof v === "object" ? `<pre>${Yaml.stringify(v)}</pre>` : v;
                items.push({ property, value });
            }
            return items;
        }
    }
};
</script>