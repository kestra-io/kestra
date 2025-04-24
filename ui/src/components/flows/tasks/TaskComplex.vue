<template>
    <el-input :model-value="JSON.stringify(values)">
        <template #append>
            <el-button
                :icon="TextSearch"
                @click="
                    breadcrumbs[breadcrumbs.length] = {
                        label: root,
                        to: {},
                        component: h(AnyOfContent, {
                            modelValue,
                            schema,
                            definitions,
                            'onUpdate:modelValue': onInput,
                        }),
                    }
                "
            />
        </template>
    </el-input>
</template>

<script setup>
    import {h, inject, ref} from "vue";
    import {BREADCRUMB_INJECTION_KEY} from "../../code/injectionKeys";

    import TextSearch from "vue-material-design-icons/TextSearch.vue";

    const breadcrumbs = inject(BREADCRUMB_INJECTION_KEY, ref([]));
</script>

<script>
    import Task from "./Task";

    export default {
        mixins: [Task],
        computed: {

            currentSchema() {
                let ref = this.schema.$ref.substring(8);
                if (this.definitions[ref]) {
                    return this.definitions[ref];
                }
                return undefined;
            },
        },
    };
</script>
