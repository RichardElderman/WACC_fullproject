
class TweetService

    @headers = {'Accept': 'application/json', 'Content-Type': 'application/json'}
    @defaultConfig = { headers: @headers }

    constructor: (@$log, @$http, @$q) ->
        @$log.debug "constructing TweetService"

    listTweets: () ->
        @$log.debug "listTweets()"
        deferred = @$q.defer()

        @$http.get("/tweets")
        .success((data, status, headers) =>
                @$log.info("Successfully listed Tweets - status #{status}")
                deferred.resolve(data)
            )
        .error((data, status, headers) =>
                @$log.error("Failed to list Tweets - status #{status}")
                deferred.reject(data)
            )
        deferred.promise

   

servicesModule.service('TweetService', ['$log', '$http', '$q', TweetService])