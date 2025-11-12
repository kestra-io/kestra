import {getCurrentInstance} from "vue"
export function useVueTour(tourName: string) {
    const instance = getCurrentInstance();
    if (!instance) {
        throw new Error("useVueTour must be called within a setup function");
    }

    const tours = instance.appContext.config.globalProperties.$tours;
    if (!tours) {
        throw new Error("VueTour is not properly installed.");
    }

    const start = () => {
        tours[tourName].start();
    };

    return {
        start
    };
}