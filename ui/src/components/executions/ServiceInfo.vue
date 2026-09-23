<template>
    <div v-if="service" class="service-info">
        <div class="heading">
            <KsId :value="service.id" :shrink="false" />
            <KsExecutionStatus
                v-if="service.state"
                size="small"
                :status="SERVICE_STATE_TO_EXECUTION_STATUS[service.state] || 'CREATED'"
                :title="service.state"
                :icon="false"
            />
        </div>
        <dl class="props">
            <KsText tag="dt" size="small">
                {{ $t("type") }}
            </KsText>
            <KsText tag="dd" size="small">
                {{ service.type }}
            </KsText>
            <KsText tag="dt" size="small">
                {{ $t("hostname") }}
            </KsText>
            <KsText tag="dd" size="small">
                {{ service.server?.hostname }}
            </KsText>
            <KsText tag="dt" size="small">
                {{ $t("version") }}
            </KsText>
            <KsText tag="dd" size="small">
                {{ service.server?.version }}
            </KsText>
        </dl>
    </div>
    <KsSkeleton v-else animated :rows="3" />
</template>

<script setup lang="ts">
    import {ref, onMounted} from "vue"
    import * as ServicesAPI from "@kestra-io/kestra-sdk/services"
    import type {ServiceInstance} from "@kestra-io/kestra-sdk"
    import {SERVICE_STATE_TO_EXECUTION_STATUS} from "../../utils/serviceState"

    const props = defineProps<{
        serviceId: string;
    }>()

    const service = ref<ServiceInstance>()

    onMounted(async () => {
        service.value = await ServicesAPI.service({id: props.serviceId})
    })
</script>

<style scoped lang="scss">
    .service-info {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        padding-inline: var(--ks-spacing-3);

        .heading {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: var(--ks-spacing-2);
        }

        .props {
            margin: 0;
            display: grid;
            grid-template-columns: max-content 1fr;
            column-gap: var(--ks-spacing-4);
            row-gap: var(--ks-spacing-1);

            dt {
                color: var(--ks-text-primary);
            }

            dd {
                margin: 0;
                color: var(--ks-text-secondary);
            }
        }
    }
</style>
