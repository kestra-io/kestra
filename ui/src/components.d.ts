import type {KsSelect, KsOption, KsButton, KsButtonGroup} from "@kestra-io/design-system"

declare module "vue" {
    interface GlobalComponents {
        KsSelect: typeof KsSelect
        KsOption: typeof KsOption
        KsButton: typeof KsButton
        KsButtonGroup: typeof KsButtonGroup
    }
}
