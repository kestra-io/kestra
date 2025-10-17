<template>
    <component :is="component" :icon="Server" @click="visible = !visible">
        <span v-if="component !== 'el-button'">
            {{ $t("worker information") }}
        </span>

        <el-dialog
            v-if="visible"
            v-model="visible"
            :id="uuid"
            destroyOnClose
            appendToBody
        >
            <template #header>
                <h5>{{ $t("worker information") }}</h5>
            </template>

            <template #default>
                <ol>
                    <li v-for="item in taskRun.attempts" :key="item.id">
                        <ServiceInfo :serviceId="String(item.workerId)" />
                    </li>
                </ol>
            </template>

            <template #footer>
                <el-button @click="visible = false">
                    {{ $t("close") }}
                </el-button>
            </template>
        </el-dialog>
    </component>
</template>

<script setup lang="ts">
    import {ref, computed} from "vue";
    import ServiceInfo from "./ServiceInfo.vue";

    import Server from "vue-material-design-icons/Server.vue";

    interface Attempt {
        id: string | number;
        workerId: string | number;
    }

    interface TaskRun {
        id: string | number;
        attempts: Attempt[];
    }

    const props = defineProps<{
        component?: string;
        taskRun: TaskRun;
    }>();

    const visible = ref(false);

    const uuid = computed(() => `workerinfo-${props.taskRun.id}`);
</script>
