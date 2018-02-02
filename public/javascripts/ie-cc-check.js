// based off https://gist.github.com/paulirish/357741

//detect IE and version number through injected conditional comments (no UA detect, no need for cond. compilation / jscript check)

//version arg is for IE version (optional)
//comparison arg supports 'lte', 'gte', etc (optional)

;(function(global) {

    'use strict';

    var $ = global.jQuery;
    var GOVUK = global.GOVUK || {};

    function IsIE(version) {
        var b = document.createElement('B'),
            docElem = document.documentElement,
            isIE;

        b.innerHTML = '<!--[if IE ' + version + ']><b id="iecctest"></b><![endif]-->';
        docElem.appendChild(b);
        isIE = !!document.getElementById('iecctest');
        docElem.removeChild(b);
        return isIE;
    }

    GOVUK.IsIE = IsIE;
    global.GOVUK = GOVUK

})(window);