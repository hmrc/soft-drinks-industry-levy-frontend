/* global $ */
/* global jQuery */
/* global GOVUK */


$(document).ready(function () {
    // Turn off jQuery animation
    jQuery.fx.off = true;

    // Where .multiple-choice uses the data-target attribute
    // to toggle hidden content
    var showHideContentFoo = new GOVUK.ShowHideContentFoo();

    showHideContentFoo.init();

    $('input.volume').keyup(function(event) {

        // format number
        $(this).val(function(index, value) {
            return value
                .replace(/\D/g, "")
                .replace(/\B(?=(\d{3})+(?!\d))/g, ",");
        });
    });

    var errorSummary = $('#error-summary-display');
    //focus error summary on page load
    if(errorSummary.length) {
        $(document).scrollTop(errorSummary.offset().top);
        $(errorSummary).focus();
    }

});

window.onload = function () {
    if (document.getElementsByClassName("flash error-summary error-summary--show").length > 0) {
        ga('send', 'event', 'validationError', 'error', window.location.pathname);
    }

    if (window.location.href.indexOf("start") > -1) {
        if (document.getElementById("registration-status").textContent.indexOf("agent") > -1) {
            ga('send', 'event', 'agents', 'visited', 'agentsPageVisited');
        } else if (document.getElementById("registration-status").textContent.indexOf("assistant") > -1) {
            ga('send', 'event', 'assistants', 'visited', 'assistantsPageVisited');
        } else if (document.getElementById("registration-status").textContent.indexOf("already registered") > -1) {
            ga('send', 'event', 'alreadyRegistered', 'visited', 'alreadyRegisteredPageVisited');
        }

    } else if (window.location.href.indexOf("verify") > -1 && document.getElementById("registration-pending-title")) {
        ga('send', 'event', 'pending', 'visited', 'pendingPageVisited');
    }

    // for returns
    if(document.getElementById('exemptions-for-small-producers-true')) {
        document.getElementById('exemptions-for-small-producers-true').addEventListener('click', function() {
            ga('send', 'event', 'smallProducerExemption', 'click', 'Yes');
        });
    }


    var arr = document.getElementsByClassName('returns-change-link');
    if (typeof arr !== "undefined") {
        for (var i = 0, len = arr.length; i < len; i++) {
            var redirectUrl = arr[i].href;
            arr[i].addEventListener('click', function (e) {
                e.preventDefault();
                gaWithCallback('send', 'event', 'changeLinks', 'click', e.pathname, function () {
                    window.location.href = redirectUrl;
                });
            });
        }
    }

    $('form[action="exemptions-for-small-producers"] button').wrap("<span id='exemptions-click-wrapper'></span>");
    $('form[action="exemptions-for-small-producers"] #exemptions-click-wrapper').attr('onclick',"ga('send', 'event', 'checkYourAnswers', 'click', 'Submit');");

    $('form[action="check-your-answers"] button').wrap("<span id='cya-click-wrapper'></span>");
    $('form[action="check-your-answers"] #cya-click-wrapper').attr('onclick',"ga('send', 'event', 'checkYourAnswers', 'click', 'Submit');");

    if (document.getElementsByTagName('h1').length > 0 && document.getElementsByTagName('h1')[0].innerText == 'Return submitted') {
        ga('send', 'event', 'visited', 'load', 'Return submitted');
    }

    if (document.getElementsByClassName("error-summary-list").length > 0) {
        ga('send', 'event', 'validationError', 'error', document.getElementsByClassName("error-summary-list").innerText);
    }

    function gaWithCallback(send, event, category, action, label, callback) {
        ga(send, event, category, action, label, {
            hitCallback: gaCallback
        });
        var gaCallbackCalled = false;
        setTimeout(gaCallback, 5000);

        function gaCallback() {
            if(!gaCallbackCalled) {
                callback();
                gaCallbackCalled = true;
            }
        }
    }

};