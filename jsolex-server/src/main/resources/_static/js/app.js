(function () {
    var lastClicked = null;

    function updateCarousel(link) {
        let carousel = document.getElementById("carousel");
        var urls = link.dataset.src;
        carousel.style.display = "block";
        // remove all the images from the carousel
        carousel.querySelector(".carousel-inner").innerHTML = "";
        let allUrls = urls.split(",");
        let captions = link.dataset.caption.split("||");
        let last = allUrls.length - 1;
        allUrls.forEach(function (url, index) {
            let item = document.createElement("div");
            let active = index === last;
            item.className = "carousel-item" + (active ? " active" : "");
            item.innerHTML = `<img src="${url}" class="d-block w-100">`;
            // Add caption
            let caption = document.createElement("div");
            caption.className = "carousel-caption d-none d-md-block";
            caption.innerHTML = "<p class='text-white'>" + captions[index] + "</p>";
            item.appendChild(caption);
            carousel.querySelector(".carousel-inner").appendChild(item);
        });
    }

    document.addEventListener("DOMContentLoaded", function () {
        document.body.addEventListener("click", function (event) {
            let link = event.target.closest(".dropdown-item");
            if (!link || !link.dataset.src) return;
            lastClicked = link;
            event.preventDefault();
            updateCarousel(link);
        });
        htmx.on("htmx:oobAfterSwap", function (evt) {
            if (evt.target.id == 'imagekinds') {
                let links = document.querySelectorAll(".dropdown-item");
                for (const link of links) {
                    if (lastClicked === null || link.dataset.id === lastClicked.dataset.id) {
                        updateCarousel(link);
                        break;
                    }
                }
            }
        });
    });
})();
