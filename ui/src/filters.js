import Vue from 'vue'


Vue.filter('cap', value => value ? value.toString().capitalize() : '')
Vue.filter('date', (dateString, format) => {
    let f
    if (format === 'full') {
        f = 'MMMM Do YYYY, h: mm: ss'
    } else if (format === 'human') {
        f = 'LLLL'
    } else {
        f = format
    }
    return Vue.moment(dateString).format(f)
})
