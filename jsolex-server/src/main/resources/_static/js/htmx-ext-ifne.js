(function () {
    let internalApi = null;
    htmx.defineExtension('oob-if-exists', {
        init: function (api) {
            internalApi = api;
        },
        transformResponse: function (text, xhr, elt) {
            const fragment = internalApi.makeFragment(text);
            const swapAttr = elt.getAttribute('hx-swap');
            if (swapAttr == 'afterbegin' || swapAttr == 'beforeend') {
                const elements = htmx.findAll(fragment, "[hx-swap-oob=if-exists]");

                for (const element of elements) {
                    const selector = '#' + element.id;
                    const existingElement = htmx.find(selector);

                    if (!!existingElement) {
                        // Move element to the desired spot and swap the HTML
                        existingElement.parentNode.insertAdjacentElement(swapAttr, existingElement);
                        element.setAttribute('hx-swap-oob', 'innerHTML');
                    } else {
                        // Just create the element
                        element.removeAttribute('hx-swap-oob');
                    }
                }
            }

            const htmlContent = [].map.call(fragment.childNodes, x => x.outerHTML).join('');
            return htmlContent;
        }
    });

})();
