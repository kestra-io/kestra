import axios from "axios";
import {defineStore} from "pinia";
import {apiUrl} from "override/utils/route";

export const useSecretsStore = defineStore("secrets", () => {
    async function find(params: {page: number, size: number, filters: {[key: string]: {EQUALS: string}}}) {
        const {data} = await axios.get(`${apiUrl()}/secrets`, {withCredentials: true, params});

        return data;
    }

    return {find};
});
