import {useI18n} from "vue-i18n";

export function useFlowOutdatedErrors(){
    const {t} = useI18n();
    function translateError(error: string): string {
        if(error.startsWith(">>>>")){
            const key = error.substring(4).trim();
            return translateErrorWithKey(key);
        } else {
            return error;
        }
    }

    function translateErrorWithKey(key: string): string {
        return `${t(key + ".description")} ${t(key + ".details")}`
    }

    return {
        translateError,
        translateErrorWithKey
    }
}