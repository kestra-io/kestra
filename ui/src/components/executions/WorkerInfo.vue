<template>
    <component :is="component" :icon="Server" @click="visible = !visible">
        <span v-if="component !== 'KsButton'">
            {{ $t("worker information") }}
        </span>

        <KsDialog
            v-if="visible"
            v-model="visible"
            :id="uuid"
            destroyOnClose
            appendToBody
        >
            <template #header>
                <h6 class="title">{{ $t("worker information") }}</h6>
            </template>

            <template #default>
                <div class="workers">
                    <div v-for="(item, index) in taskRun.attempts" :key="item.id">
                        <KsText v-if="taskRun.attempts.length > 1" tag="p" size="small" type="info" class="attempt">
                            {{ $t("attempt") }} {{ index + 1 }}
                        </KsText>
                        <ServiceInfo :serviceId="String(item.workerId)" />
                    </div>
                </div>
            </template>

            <template #footer>
                <KsButton @click="visible = false">
                    {{ $t("close") }}
                </KsButton>
            </template>
        </KsDialog>
    </component>
</template>

<script setup lang="ts">
    import {ref, computed} from "vue"
    import ServiceInfo from "./ServiceInfo.vue"
    import Server from "vue-material-design-icons/Server.vue"

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
    }>()

    const visible = ref(false)

    const uuid = computed(() => `workerinfo-${props.taskRun.id}`)
</script>

<style scoped lang="scss">
    .title {
        font-weight: var(--ks-font-weight-semibold);
    }

    .workers {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-3);

        > div + div {
            border-top: var(--ks-border-block-secondary);
            padding-top: var(--ks-spacing-3);
        }
    }

    .attempt {
        margin-bottom: var(--ks-spacing-1);
        padding-inline: var(--ks-spacing-3);
    }
</style>
