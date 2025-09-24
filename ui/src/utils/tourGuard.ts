import {useExecutionsStore} from "../stores/executions";
import {useFlowStore} from "../stores/flow";

export const ALLOWED_ROUTES = ["welcome"];

export const shouldShowWelcome = () => {
    const executionsStore = useExecutionsStore();
    const flowStore = useFlowStore();
    return !executionsStore.total && !flowStore.overallTotal;
};

export const isAllowedRoute = (routeName: string) => {
    return ALLOWED_ROUTES.includes(routeName);
};