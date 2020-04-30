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
        $("input[id^=" + "start-date" + "]").each(function(index) {
            $(this).addClass('form-control-error')
        })
        $("input[id^=" + "cancel-registration-date" + "]").each(function(index) {
                    $(this).addClass('form-control-error')
        })

    }

    //The focus setting behaviour defined in Assests is having troubles with different browsers. So we need to override the behaviour to make it work across all browsers
    $('.error-summary a').on('click', function (e) {
        e.preventDefault()
        var $this = $(this);
        var focusId = $this.attr('data-focuses');
        $("input[id^=" + removeSpecialChars(focusId) + "]:eq(" + 0 + ")").trigger('focus');
        $('[id="'+ removeSpecialChars(focusId) + '-true' + '"]').trigger('focus');
        var indexSetToFocus = 0;
        //Treat Start dates as special entities
        $("input[id^=" + "start-date" + "]").each(function(index) {
            if($.trim($(this).val()) == "") {
                indexSetToFocus = index;
                return false;
            }
        });
        $("input[id^=" + "start-date" + "]:eq(" + indexSetToFocus + ")").trigger('focus');

        $("input[id^=" + "cancel-registration-date" + "]").each(function(index) {
            if($.trim($(this).val()) == "") {
                indexSetToFocus = index;
                return false;
            }
                });
        $("input[id^=" + "cancel-registration-date" + "]:eq(" + indexSetToFocus + ")").trigger('focus');
    })
});

    function removeSpecialChars( myid ) {
        return myid.replace( /(:|\.|\[|\]|,|=|@)/g, "\\$1" );
    }

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

    $('form[action="exemptions-for-small-producers"] button').wrap("<span id='exemptions-click-wrapper'></span>");
    $('form[action="exemptions-for-small-producers"] #exemptions-click-wrapper').attr('onclick',"ga('send', 'event', 'smallProducerExemptionButton', 'click', 'Submit');");

    $('form[action="check-your-answers"] button').wrap("<span class='cya-click-wrapper'></span>");
    $('form[action="check-your-answers"] .cya-click-wrapper').attr('onclick',"ga('send', 'event', 'checkYourAnswers', 'click', 'Submit');");

    if (document.getElementsByTagName('h1').length > 0 && document.getElementsByTagName('h1')[0].innerText == 'Return sent') {
        ga('send', 'event', 'visited', 'load', 'Return submitted');
    }

    if (document.getElementsByClassName("error-summary-list").length > 0) {
        ga('send', 'event', 'validationError', 'error', document.getElementsByClassName("error-summary-list").innerText);
    }


    $(".returns-change-link").click(function(event) {
        event.preventDefault();
        var redirectUrl = $(this).attr('href');
        gaWithCallback('send', 'event', 'changeLinks', 'click', redirectUrl, function() {
            window.location.href = redirectUrl;
        });
    });



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


    $('form[action="organisation-type"] button').wrap("<span id='org-type-click-wrapper'></span>");
    var radioValue = $("input[name='organisation-type']:checked").val();
    $('form[action="organisation-type"] #org-type-click-wrapper').attr('onclick',"ga('send', 'event', 'orgType', 'selectOrg', '"+radioValue+"');");
    //
    // $("#org-type-click-wrapper").click(function(){
    //
    //
    //
    //     if(radioValue){
    //
    //         confirm("Your are a - " + radioValue);
    //
    //     }
    //
    // });

};