/*
 * This sample code is a preliminary draft for illustrative purposes only and not subject to any license granted by Wincor Nixdorf.
 * The sample code is provided “as is” and Wincor Nixdorf assumes no responsibility for errors or omissions of any kind out of the
 * use of such code by any third party.
 */

// mock bridge object for easy browser testing
if (window.Bridge === undefined) {
    Bridge = {
        navigate: function (fragment) {
            window.location.hash = '#' + fragment;
        },
        buyTicket: function (movieId, amount) {
            console.log("buy ticket for", movieId, amount);
            this.navigate("/success/" + movieId);
        },
        disableScroll: function () {
            console.log("disable scrolling");
        },
        enableScroll: function () {
            console.log("enable scrolling");
        },
        printTicket: function (title) {
            console.log("printing ticket for", title);
        },
        exit: function () {
            console.log("exit");
        },
        rewriteUri: function (uri) {
            return uri;
        },
        isAeviDevice: function () {
            return false;
        },
        currencyCode: function () {
            return "EUR";
        },
        currencySymbol: function () {
            return "&euro";
        },
        log: function (message) {
            console.log(message);
        }
    }
}

/**
 *  To the movies! app code
 */
;
(function () {

    var api_url_original = "https://api.themoviedb.org/3";
    var api_key = "b4b100d1055bb5ec2219ed64270ab0d7"

    var configuration;
    var movies;

    var _app = function (fraction) {
        return (api_url_original + fraction + "?api_key=" + api_key);
    }

    var getJSON = function (url, onSuccess) {
        Bridge.log("retrieving information from:" + url);
        $.ajax({
            url: url,
            dataType: 'jsonp',
            success: onSuccess,
            cache: false,
            timeout: 16000,
            error: function (jqXHR, status, errorThrown) {
                Bridge.log("retrieving url:" + url + " resulted in error with status:" + status);
                Bridge.navigate("/load-error");
            }
        });
    }

    /**
     * Loads the base URL and other configuration keys
     */
    var loadConfiguration = function () {

        Bridge.log("loading configuration");
        Bridge.log("default currency:" + Bridge.currencyCode());
        Bridge.log("default currency symbol:" + Bridge.currencySymbol());

        var onSuccess =
                function (data) {
                    configuration = data;
                    configuration.poster_size = "w342"
                    configuration.backdrop_size = "w780"
                };

        getJSON(_app("/configuration"), onSuccess);
    }

    /**
     *  Loads the list of upcoming movies
     */
    var loadMovies = function () {

        Bridge.log("loading movies");

        var onSuccess =
                function (data) {
                    movies = _.filter(data.results, function (e) {
                        return e.adult === false && e.backdrop_path !== null
                    });

                    var view = _.map(movies, function (movie) {
                        return {
                            id: movie.id,
                            title: movie.title,
                            image: configuration.images.secure_base_url + configuration.poster_size + movie.poster_path,
                            backdrop: configuration.images.secure_base_url + configuration.backdrop_size + movie.backdrop_path,
                            price: "14.00",
                            fee: "1.50",
                            total: "15.50",
                            currency: Bridge.currencySymbol(),
                            date: function () {
                                var date = Date.parse(movie.release_date);
                                return date.getDay() + "-" + (date.getMonth() + 1) + "-" + date.getFullYear();
                            }
                        }
                    });

                    $("#content").html(Mustache.render($("#movie-template").text(), {movies: view}));
                    $("#content > article").on('click', function (event) {
                        Bridge.navigate("/movie/" + $(this).data("movieid"))
                    });

                    $("#content").show();

                    $("#content-detail, #message").height($("#content").height());
                };

        getJSON(_app("/movie/now_playing"), onSuccess);
    };

    /**
     *  Update the UI to show the list of movies
     */
    var showMovieListing = function () {
        Bridge.log("showing events");

        if (movies === undefined) {
            loadMovies();
        }

        Bridge.enableScroll();
        $("#slider-wrapper").animate({ marginLeft: 0 }, 500);
    };

    /**
     *  Show the movie detail page with the option to buy a ticket
     */
    var showTicketOrderPage = function (movieId) {

        Bridge.log("showing buy ticket page");

        $.getJSON(_app("/movie/" + movieId), function (data) {

            var article = $("article[data-movieId='" + movieId + "']");

            var view = {
                id: movieId,
                overview: data.overview,
                image: article.data("image"),
                backdrop: article.data("backdrop"),
                title: article.data("title"),
                price: article.data("price"),
                fee: article.data("fee"),
                currency: article.data("currency"),
                total: article.data("total"),
            };

            $("#content-detail").html(Mustache.render($("#event-tickets-template").text(), view));
            $("#content-detail").css("top", $(document).scrollTop());

            $("#buyticket").on('click', function () {
                $("#buyticket").prop("disabled", true);
                $("#buyticket").text("please wait");
                Bridge.buyTicket(movieId.toString(), article.data("total").toString());
            });

            Bridge.disableScroll();

            var slideWidth = parseInt($(".slide").width());
            $("#slider-wrapper").animate({ marginLeft: -slideWidth }, 500);
        });
    };

    /**
     *  Print the ticket and show a thank you message
     */
    var showSuccessPage = function (movieId) {

        Bridge.log("ticket bought");

        var article = $("article[data-movieId='" + movieId + "']");

        var view = {
            id: movieId,
            image: article.data("image"),
            title: article.data("title"),
            price: article.data("price"),
            fee: article.data("fee"),
            currency: article.data("currency"),
            total: article.data("total"),
        };

        $("#message").html(Mustache.render($("#thanks-template").text(), view));
        $("#message").css("top", $(document).scrollTop());

        $("#return-to-start").on('click', function () {
            Bridge.navigate("/");
        });

        var slideWidth = parseInt($(".slide").width());
        $("#slider-wrapper").animate({ marginLeft: -slideWidth * 2 }, 500);

        Bridge.printTicket(view.title);
    }

    /**
     *  Show a friendly error message in case of payment failure
     */
    var showFailurePage = function (ret_status, ret_errorcode) {

        Bridge.log("payment failed");

        var view = {
            status: ret_status,
            errorcode: ret_errorcode
        };

        $("#message").html(Mustache.render($("#failure-template").text(), view));

        var slideWidth = parseInt($(".slide").width());
        $("#slider-wrapper").animate({ marginLeft: -slideWidth * 2 }, 500);

        $("html, body").animate({ scrollTop: 0 }, "fast");

        $("#return-to-start").on('click', function () {
            Bridge.navigate("/");
        });
    }

    /**
     *  Shows a friendly error message when the loading of data failed
     */
    var showLoadErrorPage = function () {
        Bridge.log("load error")

        $("#message").html(Mustache.render($("#load-error-template").text()));
        $("#message").css("top", $(document).scrollTop());

        $("#exit-app").on('click', function () {
            Bridge.exit();
        });

        var slideWidth = parseInt($(".slide").width());
        $("#slider-wrapper").animate({ marginLeft: -slideWidth }, 500);
    }

    /**
     *  main entry point of the app
     */
    $("#slider-wrapper").width($(".slide").length * $(".slide").width());

    loadConfiguration();

    Bridge.log("init router")
    var routes = {
        '/': showMovieListing,
        '/movie/:movieId': showTicketOrderPage,
        '/success/:movieId': showSuccessPage,
        '/failure/:status/:errorcode': showFailurePage,
        '/load-error': showLoadErrorPage,
    };

    var router = Router(routes);
    router.init();

    // go go!
    Bridge.navigate("/");
})();
