<template>
    <el-input :model-value="JSON.stringify(values)">
        <template #append>
            <el-button
                :icon="Eye"
                @click="
                    () => {
                        $store.commit('code/addBreadcrumbs', {
                            breadcrumb: {
                                label: root,
                                to: {},
                                component: h(OneOfContent, {
                                    modelValue,
                                    schema,
                                    definitions,
                                    'onUpdate:modelValue': onInput,
                                }),
                            },
                            position:
                                breadcrumbs.length === 2
                                    ? 2
                                    : breadcrumbs.length,
                        });
                    }
                "
            />
        </template>
    </el-input>
</template>

<script setup>
    import {h} from "vue";
    import Eye from "vue-material-design-icons/Eye.vue";
    import OneOfContent from "./OneOfContent.vue";
</script>

<script>
    import Task from "./Task";
    import {mapState} from "vuex";

    export default {
        mixins: [Task],

        computed: {
            ...mapState("code", ["breadcrumbs"]),
        },
    };
</script>
