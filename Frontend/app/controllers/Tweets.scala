package controllers

import play.modules.reactivemongo.MongoController
import play.modules.reactivemongo.json.collection.JSONCollection
import scala.concurrent.Future
import reactivemongo.api.Cursor
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import org.slf4j.{LoggerFactory, Logger}
import javax.inject.Singleton
import play.api.mvc._
import play.api.libs.json._
import akka.actor._
import play.api.Play.current

/**
 * The Users controllers encapsulates the Rest endpoints and the interaction with the MongoDB, via ReactiveMongo
 * play plugin. This provides a non-blocking driver for mongoDB as well as some useful additions for handling JSon.
 * @see https://github.com/ReactiveMongo/Play-ReactiveMongo
 */
@Singleton
class Tweets extends Controller with MongoController {

  private final val logger: Logger = LoggerFactory.getLogger(classOf[Tweets])
  
  private var tweetArray : Future[JsArray] = findTweets

  /*
   * Get a JSONCollection (a Collection implementation that is designed to work
   * with JsObject, Reads and Writes.)
   * Note that the `collection` is not a `val`, but a `def`. We do _not_ store
   * the collection reference to avoid potential problems in development with
   * Play hot-reloading.
   */
  def collection: JSONCollection = db.collection[JSONCollection]("tweets")

  import models._
  import models.JsonFormats._
  
  // WEBSOCKETS #############################################
  import play.api.libs.iteratee._
  import play.api.libs.concurrent.Execution.Implicits.defaultContext


  // WEBSOCKET SERVER: NOT USED YET (URL UNKNOWN)
  def socket = WebSocket.using[String] { request =>

      // no need to do something on input
      val in = Iteratee.ignore[String]
    
      // Send a single 'Hello!' message
      val out = Enumerator("Hello!")
    
      (in, out)
  }
  
  /* WEBSOCKET CLIENT: GIVES ERRORS (E.G. WebSocket needs parameters)
  def createSocket: WebSocket = {
      var s = new WebSocket("ws://echo.websocket.org/") // url for test purposes, change into url of server "socket" above
      s.onopen = function() { s.send("[hey!]") }
      s.onclose = function(e) { console.log("closed!",e); }
      s.onmessage = function(msg) {console.log("got: ", msg.data); }
      return s;
  }
  var websocket = createSocket;*/

  
  // RABBITMQ ##############################################
  import akka.actor.ActorSystem
  import akka.actor.ActorRef

  import com.thenewmotion.akka.rabbitmq._
  implicit val system = ActorSystem()
  val factory = new ConnectionFactory()
  val rabbitConnection = system.actorOf(ConnectionActor.props(factory), "rabbitmq")
  val exchange = "amq.fanout"
  
  def setupSubscriber(channel: Channel, self: ActorRef) {
    val queue = channel.queueDeclare().getQueue
    channel.queueBind(queue, exchange, "")
    val consumer = new DefaultConsumer(channel) {
      override def handleDelivery(consumerTag: String, envelope: Envelope, properties: BasicProperties, body: Array[Byte]) {
        println("received: " + fromBytes(body))
        tweetArray = findTweets
        //websocket.send("some msg")// send message via websocket, does not work 
      }
    }
    channel.basicConsume(queue, true, consumer)
  }
  rabbitConnection ! CreateChannel(ChannelActor.props(setupSubscriber), Some("subscriber"))
  
  def fromBytes(x: Array[Byte]) = new String(x, "UTF-8")
  def toBytes(x: Long) = x.toString.getBytes("UTF-8")
  // END RABBITMQ ############################################
  
  // return array containing latest tweets
  def getNewTweets = Action.async{
    tweetArray.map {
      tweets =>
        Ok(tweets(0))
    }
  }
  
  // find newest tweets in mongodb and store them in array
  def findTweets : Future[JsArray] = {
    // let's do our query
    val cursor: Cursor[tweet] = collection.
      // find all
      find(Json.obj()).
      // sort them by creation date
      sort(Json.obj("count" -> -1)).
      // perform the query and get a cursor of JsObject
      cursor[tweet]

    // gather all the JsObjects in a list
    val futureTweetsList: Future[List[tweet]] = cursor.collect[List]()

    // return list transformed into a JsArray
    return futureTweetsList.map { tweets =>
      Json.arr(tweets)
    }
  }
}

/* 
BELOW CODE SNIPPETS THAT MIGHT INCLUDE SOMETHING RELEVANT

in tweetctrl.coffee:
 $(document).ready ->
      log = (msg) -> $('#log').append("#{msg}<br />")
      serverUrl = 'ws://127.0.0.1:8000/demo'
      if window.MozWebSocket
        socket = new MozWebSocket serverUrl
      else if window.WebSocket
        socket = new WebSocket serverUrl
      socket.binaryType = 'blob'
    
      socket.onopen = (msg) ->
        $('#status').removeClass().addClass('online').html('connected')
    
      socket.onmessage = (msg) ->
        getAllTweets
        log("Action: #{response.action}")
        log("Data: #{response.data}")
    
      socket.onclose = (msg) ->
        $('#status').removeClass().addClass('offline').html('disconnected')
    
      $('#status').click ->
        socket.close()
    
      $('#send').click ->
        payload = new Object()
        payload.action = $('#action').val()
        payload.data = $('#data').val()
        socket.send(JSON.stringify(payload))
    
      $('#sendfile').click ->
        data = document.binaryFrame.file.files[0]
        if data
          payload = new Object()
          payload.action = 'setFilename'
          payload.data = $('#file').val()
          socket.send JSON.stringify payload
          socket.send(data)
        return false
        
        
        
in TweetCtrl.coffee:        
        
        
    startWebSocket: () ->
        @ws = new WebSocket $("body").data("ws-url")
        @ws.onmessage = (event) ->
            @getAllTweets()
        
        
in view.html:
        <script language="javascript">
            var connection = new WebSocket("ws://" + location.host + "/whatever");
            connection.onopen = function(event) {
                console.log(“Connection established”);
            }
            connection.onclose = function(event) {
                console.log(“Disconnected”);
            }
            connection.onerror = function(event) {
                console.log(“ERROR websocket”);
            }
            connection.onmessage = function(event) {
                console.log(“Message received”);
                tc.getAllTweets();
            }
            
        </script>
        
        
in TweetCtrl.coffee:
        
        
  websocket.onmessage = function(str) {
    console.log("Someone sent: ", str);
  };

  // Tell the server this is client 1 (swap for client 2 of course)
  websocket.send(JSON.stringify({
    id: 1
  }));

  // Tell the server we want to send something to the other client
  websocket.send(JSON.stringify({
    to: 2,
    data: "new"
  }));
        */
