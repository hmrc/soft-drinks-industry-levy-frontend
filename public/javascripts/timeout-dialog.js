
if(window.location.pathname !== "/soft-drinks-industry-levy/time-out") {
$.timeoutDialog({
    timeout: 2,
    countdown: 5,
    keep_alive_url: window.location.href,
    restart_on_yes: true,
    logout_url: '/soft-drinks-industry-levy/time-out',
    keep_alive_button_text: 'Stay signed in',
    sign_out_button_text: 'Sign out'
})};