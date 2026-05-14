import type {RouterLink} from "vue-router"

type RouterLinkTo = InstanceType<typeof RouterLink>["$props"]["to"];

export interface BreadcrumbItem {
    label: string;
    link?: RouterLinkTo;
    disabled?: boolean;
}
