/* global $ */
/* global jQuery */
/* global GOVUK */


$(document).ready(function () {
    // Turn off jQuery animation
    jQuery.fx.off = true;

    // Where .multiple-choice uses the data-target attribute
    // to toggle hidden content
    var showHideContent = new GOVUK.ShowHideContent();

    showHideContent.init()
});

window.onload = function () {
    if(document.getElementsByClassName("flash error-summary error-summary--show").length > 0) {
        ga('send', 'event', 'validationError', 'error', window.location.pathname);
    }

};