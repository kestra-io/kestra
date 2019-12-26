import Vue from 'vue';
import VueAxios from 'vue-axios';
import axios from 'axios';

export default callback => {
    Vue.use(
        VueAxios,
        axios.create({
            timeout: 15000,
            headers: {
                'Content-Type': 'application/json'
            }
        })
    );

    Vue.axios.defaults.baseURL = (process.env.VUE_APP_API_URL || "") + "/";
    callback();
};
