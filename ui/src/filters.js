import Vue from 'vue'
import humanizeDuration from "humanize-duration";

Vue.filter('id', value => value ? value.toString().substr(0, 8) : '');

Vue.filter('humanizeDuration', (value, options) => {
    options = options || { maxDecimalPoints: 2 }
    options.language = localStorage.getItem('lang') || 'en'
    return humanizeDuration(value * 1000, options)
});

Vue.filter('cap', value => value ? value.toString().capitalize() : '');

Vue.filter('lower', value => value ? value.toString().toLowerCase() : '');

Vue.filter('date', (dateString, format) => {
    let f;
    if (format === 'full') {
        f = 'MMMM Do YYYY, h: mm: ss'
    } else if (format === 'human') {
        f = 'LLLL'
    } else {
        f = format
    }
    return Vue.moment(dateString).format(f)
});
Vue.filter('ellipsis', (text, len) => text.length > len ? text.substr(0, len) + '...' : text.substr(0, len))