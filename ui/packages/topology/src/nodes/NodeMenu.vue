<template>
    <KsIconButton
        v-if="actions.length === 1"
        :tooltip="actions[0].tooltip ?? actions[0].label"
        :aria-label="actions[0].label"
        class="node-action-button"
        @click.stop="actions[0].onClick()"
    >
        <component
            :is="actions[0].icon"
            :class="{'text-danger': actions[0].danger}"
            :alt="actions[0].label"
        />
    </KsIconButton>

    <KsDropdown v-else-if="actions.length > 1" trigger="click" placement="right-start" @click.stop>
        <KsIconButton aria-label="More actions" class="node-action-button">
            <DotsVertical alt="More actions" />
        </KsIconButton>
        <template #dropdown>
            <KsDropdownMenu>
                <KsDropdownItem
                    v-for="action in actions"
                    :key="action.key"
                    :divided="action.divided"
                    @click="action.onClick()"
                >
                    {{ action.label }}
                </KsDropdownItem>
            </KsDropdownMenu>
        </template>
    </KsDropdown>
</template>

<script setup lang="ts">
    import {KsIconButton, KsDropdown, KsDropdownMenu, KsDropdownItem} from "@kestra-io/design-system"
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue"

    export interface NodeAction {
        key: string;
        label: string;
        icon: unknown;
        tooltip?: string;
        danger?: boolean;
        divided?: boolean;
        onClick: () => void;
    }

    defineOptions({name: "NodeMenu", inheritAttrs: false})

    defineProps<{actions: NodeAction[]}>()
</script>

