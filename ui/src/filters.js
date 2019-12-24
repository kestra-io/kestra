import Vue from 'vue'


Vue.filter('id', value => value ? value.toString().substr(0, 8) : '')
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
