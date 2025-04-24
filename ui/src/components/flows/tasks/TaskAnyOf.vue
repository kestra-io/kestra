<template>
    <el-input :model-value="JSON.stringify(values)">
        <template #append>
            <el-button
                :icon="Eye"
                @click="
                    () => {
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
                    }
                "
            />
        </template>
    </el-input>
</template>

<script setup>
    import {h, inject, ref} from "vue";
    import Eye from "vue-material-design-icons/Eye.vue";
    import AnyOfContent from "./AnyOfContent.vue";

    import {BREADCRUMB_INJECTION_KEY} from "../../code/injectionKeys";

    const breadcrumbs = inject(BREADCRUMB_INJECTION_KEY, ref([]));
</script>

<script>
    import Task from "./Task";

    export default {
        mixins: [Task],
    };
</script>
