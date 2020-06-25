(function (d, w) {
  
  function ready(fn) {
    if (d.readyState != 'loading') {
      fn();
    } else {
      d.addEventListener('DOMContentLoaded', fn);
    }
  }

  function toggleClass(el, className) {
    if (el.classList) {
      el.classList.toggle(className);
    } else {
      var classes = el.className.split(' ');
      var existingIndex = classes.indexOf(className);

      if (existingIndex >= 0)
        classes.splice(existingIndex, 1);
      else
        classes.push(className);

      el.className = classes.join(' ');
    }
  }

  function removeClass(el, className) {
    if (el.classList)
      el.classList.remove(className);
    else
      el.className = el.className.replace(new RegExp('(^|\\b)' + className.split(' ').join('|') + '(\\b|$)', 'gi'), ' ');
  }

  function addClass(el, className) {
    if (el.classList)
      el.classList.add(className);
    else
      el.className += ' ' + className;
  }

  ready(function() {
    var mobileMediaQuery = '(max-width: 641px)'
    var mq = w.matchMedia(mobileMediaQuery)
    if(!mq.matches) { jsTabs() }
  })

  function jsTabs() {
    var tabs = d.querySelectorAll('.govuk-tabs')
    var tabSelectedClass = 'govuk-tabs__list-item--selected'
    var panelHiddenClass = 'govuk-tabs__panel--hidden'

    Array.prototype.forEach.call(tabs, function (tabsEl, i) {
      
      var tabLinks = tabsEl.querySelectorAll('.govuk-tabs__tab')
      var tabPanels = tabsEl.querySelectorAll('.govuk-tabs__panel')
      var tabList = tabsEl.querySelectorAll('.govuk-tabs__list-item')

      Array.prototype.forEach.call(tabLinks, function (tabLink) {
        
        //setup event listener for tabs
        tabLink.addEventListener('click', function(event) {
          event.preventDefault()
          tabClick(this)
        })

      })

        Array.prototype.forEach.call(tabList, function (tabListItem) {

            //setup event listener for tabs
            tabListItem.addEventListener('click', function(event) {
                event.preventDefault()
                tabListClick(this)
            })

        })

        function tabListClick(tab) {
            //remove all tabSelectedClass from all tabs
            Array.prototype.forEach.call(tabList, function (tabListItem) {
                removeClass(tabListItem, tabSelectedClass)
            })

            //add selected class to the selected tab
            addClass(tab, tabSelectedClass)
        }

      function tabClick(tab) {
        
        //remove all tabSelectedClass from all tabs
        //change aria-selected to false for all tabs
        Array.prototype.forEach.call(tabLinks, function (tabLink) {
          tabLink.setAttribute('aria-selected', 'false')
        })

        //change aria-selected to true for the selected tab

        tab.setAttribute('aria-selected', 'true')

        //hide all the panels
        Array.prototype.forEach.call(tabPanels, function (tabPanel) {
          addClass(tabPanel, panelHiddenClass)
        })

        //show the target panel
        var targetPanel = tab.getAttribute('href')
        var panel = tabsEl.querySelector(targetPanel)
        removeClass(panel, panelHiddenClass)
      }
      

    })
  }
  
})(document,window);