<template>
    <component
        :is="component"
        v-if="service != null"
    >
        <template #default>
            <strong>{{ service.id }}</strong>: {{ t("hostname") }}={{ service.server.hostname }}, {{ t("version") }}={{ service.server.version }}, {{ t("state") }}={{ service.state }}
        </template>
    </component>
</template>

<script setup lang="ts">
    import {ref, onMounted} from "vue";
    import {useServiceStore} from "../../stores/service";
    import {useI18n} from "vue-i18n";

    interface Props {
        component?: string;
        serviceId: string;
    }

    const props = withDefaults(defineProps<Props>(), {
        component: "b-button"
    });

    defineEmits<{
        follow: []
    }>();

    const {t} = useI18n();
    const serviceStore = useServiceStore();
    const service = ref();

    const load = async () => {
        service.value = await serviceStore.findServiceById({id: props.serviceId});
    };

    onMounted(() => {
        load();
    });
</script>