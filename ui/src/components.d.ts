import type {KsSelect, KsOption, KsButton, KsButtonGroup} from "@kestra-io/ui-design-system"

declare module "vue" {
    interface GlobalComponents {
        KsSelect: typeof KsSelect
        KsOption: typeof KsOption
        KsButton: typeof KsButton
        KsButtonGroup: typeof KsButtonGroup
    }
}
