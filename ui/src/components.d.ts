import type {KsSelect, KsOption, KsButton} from "@kestra-io/ui-design-system"

declare module "vue" {
    interface GlobalComponents {
        KsSelect: typeof KsSelect
        KsOption: typeof KsOption
        KsButton: typeof KsButton
    }
}
