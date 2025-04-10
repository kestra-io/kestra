<template>
    <component
        :is="component"
        :icon="Server"
        @click="visible = !visible"
    >
        <span v-if="component !== 'el-button'">{{ $t('worker information') }}</span>

        <el-dialog v-if="visible" v-model="visible" :id="uuid" destroy-on-close :append-to-body="true">
            <template #header>
                <h5>{{ $t("worker information") }}</h5>
            </template>

            <template #default>
                <ol>
                    <li v-for="item in taskRun.attempts" :key="item.id">
                        <ServiceInfo :service-id="item.workerId" />
                    </li>
                </ol>
            </template>

            <template #footer>
                <el-button @click="visible = false">
                    {{ $t('close') }}
                </el-button>
            </template>
        </el-dialog>
    </component>
</template>

<script setup>
    import Server from "vue-material-design-icons/Server.vue";
</script>

<script>
    import ServiceInfo from "./ServiceInfo.vue";

    export default {
        components: {ServiceInfo},
        props: {
            component: {
                type: String,
                default: "b-button"
            },
            taskRun: {
                type: Object,
                required: false,
                default: undefined
            }
        },
        emits: ["follow"],
        computed: {
            uuid() {
                return "workerinfo-" + this.taskRun.id;
            },
        },
        data() {
            return {
                visible: false,
            };
        },
    };
</script>