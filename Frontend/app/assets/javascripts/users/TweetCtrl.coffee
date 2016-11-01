
class TweetCtrl

        
    constructor: (@$log, @TweetService) ->
        @$log.debug "constructing TweetController"
        @tweets = []
        @socket
        @createSocket()
        @getAllTweets()
    
    # websocket client, may work but cannot be tested yet
    createSocket: () ->
        serverUrl = "ws://echo.websocket.org/" # standard url for test purposes, change into url of server in Tweets.scala
        @socket = new WebSocket serverUrl
        @socket.binaryType = 'blob'
        @$log.debug "socket created??"
        
        # if message received, get latest tweets from Tweets.scala
        @socket.onmessage = (msg) ->
            @getAllTweets()
        
        
    
    getAllTweets: () ->
        @$log.debug "getAllTweets()"

        @TweetService.listTweets()
        .then(
            (data) =>
                @$log.debug "Promise returned #{data.length} Tweets"
                @tweets = data
            ,
            (error) =>
                @$log.error "Unable to get Tweets: #{error}"
            )

controllersModule.controller('TweetCtrl', ['$log', 'TweetService', TweetCtrl])