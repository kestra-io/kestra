<template>
    <component
        :is="component"
        v-if="service != null"
    >
        <template #default>
            <strong>{{ service.id }}</strong>: {{ $t("hostname") }}={{ service.server.hostname }}, {{ $t("version") }}={{ service.server.version }}, {{ $t("state") }}={{ service.state }}
        </template>
    </component>
</template>

<script>
    export default {
        props: {
            component: {
                type: String,
                default: "b-button"
            },
            serviceId: {
                type: String,
                required: true
            }
        },
        emits: ["follow"],
        methods: {
            load() {
                this.$store.dispatch("service/findServiceById", {id: this.serviceId}).then(service => {
                    this.service = service;
                });
            },
        },
        computed: {
            uuid() {
                return "serviceinfo-" + this.serviceId;
            },
        },
        mounted() {
            this.load();
        },
        data() {
            return {
                service: undefined,
            };
        },
    };
</script>