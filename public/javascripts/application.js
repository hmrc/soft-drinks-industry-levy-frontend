/* global $ */
/* global jQuery */
/* global GOVUK */


$(document).ready(function () {
    // Turn off jQuery animation
    jQuery.fx.off = true;

    // Where .multiple-choice uses the data-target attribute
    // to toggle hidden content
    var showHideContent = new GOVUK.ShowHideContent();

    function isIE(version) { new GOVUK.IsIE(version) }

    //disable show/hide js when running IE 8 or IE 9, as the show functionality won't work
    if(!isIE(8) && !isIE(9)) {
        showHideContent.init()
    }
});
