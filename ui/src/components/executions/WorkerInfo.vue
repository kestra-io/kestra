<template>
    <component
        :is="component"
        :icon="Server"
        @click="visible = !visible"
    >
        <span v-if="component !== 'el-button'">
                {{ $t('worker information') }}
        </span>

        <el-dialog
            v-if="visible"
            v-model="visible"
            :id="uuid"
            destroy-on-close
            :append-to-body="true"
        >
            <template #header>
                <h5>{{ $t('worker information') }}</h5>
            </template>

            <ol>
                <li v-for="item in taskRun?.attempts || []" :key="item.id">
                    <ServiceInfo :serviceId="item.workerId" />
                </li>
            </ol>

            <template #footer>
                <el-button @click="visible = false">
                    {{ $t('close') }}
                </el-button>
            </template>
        </el-dialog>
    </component>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import Server from "vue-material-design-icons/Server.vue";
import ServiceInfo from "./ServiceInfo.vue";

interface Attempt {
    id: string | number;
    workerId: string | number;
}

interface TaskRun {
    id: string | number;
    attempts: Attempt[]; 
}

const props = withDefaults(
    defineProps<{
        component?: string;
        taskRun?: TaskRun;
    }>(),
    { component: "b-button" }
);

const visible = ref(false);

const uuid = computed(() => {
    return props.taskRun ? `workerinfo-${props.taskRun.id}` : '';
});
</script>
