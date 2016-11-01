import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import com.hunorkovacs.koauth.domain.KoauthRequest
import com.hunorkovacs.koauth.service.consumer.DefaultConsumerService
import com.typesafe.config.ConfigFactory
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Try, Failure, Success}
import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks._
import org.json4s._
import org.json4s.native.JsonMethods._
import reactivemongo.bson.{BSONDocumentWriter, BSONDocument, BSONDocumentReader, BSONObjectID}
import com.typesafe.config.ConfigFactory
import reactivemongo.api.MongoDriver
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import reactivemongo.api.QueryOpts
import reactivemongo.api.collections.default.BSONCollection
import com.mongodb.casbah.Imports._
import com.thenewmotion.akka.rabbitmq._

object TwitterStreamer extends App {
	val conf = ConfigFactory.load()
	val consumerKey = "KzyWflWxPSeeenM3RliLVlxNf"
	val consumerSecret = "vZ2gLuQaElZnbevFICDpbYkjoU5PcS9rZ4s0fa4hhmMkxaZYew"
	val accessToken = "777246150940385280-eptYHnnTkta86z76ysHb6bS32lJWlKi"
	val accessTokenSecret = "fFB4QuBPGEP1rQrHNKxNbudute3Hkt7aEbTahpf3HsF3j"
	val url = "https://stream.twitter.com/1.1/statuses/filter.json"
	
	implicit val system = ActorSystem()
	implicit val materializer = ActorMaterializer()
	implicit val formats = DefaultFormats
	val consumer = new DefaultConsumerService(system.dispatcher)
	
	//RabbitMQ
	val factory = new ConnectionFactory()
	val connection = system.actorOf(ConnectionActor.props(factory), "rabbitmq")
	val exchange = "amq.fanout"

	def setupPublisher(channel: Channel, self: ActorRef) {
		val queue = channel.queueDeclare().getQueue
		channel.queueBind(queue, exchange, "")
	}
	connection ! CreateChannel(ChannelActor.props(setupPublisher), Some("publisher"))

	def fromBytes(x: Array[Byte]) = new String(x, "UTF-8")
	def toBytes(x: Long) = x.toString.getBytes("UTF-8")

	
	
	//Filter tweets by a term "MAGA"
	val body = "track=MAGA"
	println("Searching for tweets where " + body)
	val source = Uri(url)
	//Buffer that stores the tweets
	var tweetBuffer = ArrayBuffer[Tweet]()

	//Create Oauth 1a header
	val oauthHeader: Future[String] = consumer.createOauthenticatedRequest(
		KoauthRequest(
			method = "POST",
			url = url,
			authorizationHeader = None,
			body = Some(body)
		),
		consumerKey,
		consumerSecret,
		accessToken,
		accessTokenSecret
	) map (_.header)
	
	oauthHeader.onComplete {
		case Success(header) =>
			val httpHeaders: List[HttpHeader] = List(
				HttpHeader.parse("Authorization", header) match {
					case ParsingResult.Ok(h, _) => Some(h)
					case _ => None
				},
				HttpHeader.parse("Accept", "*/*") match {
					case ParsingResult.Ok(h, _) => Some(h)
					case _ => None
				}
			).flatten
			val httpRequest: HttpRequest = HttpRequest(
				method = HttpMethods.POST,
				uri = source,
				headers = httpHeaders,
				entity = HttpEntity(contentType = ContentType(MediaTypes.`application/x-www-form-urlencoded`), string = body)
			)
			val request = Http().singleRequest(httpRequest)
			request.flatMap { response =>
				if (response.status.intValue() != 200) {
					println(response.entity.dataBytes.runForeach(_.utf8String))
					Future(Unit)
				} else {
					var count = 0
					println("Request is okay (200)")
					response.entity.dataBytes
						.scan("")((acc, curr) => if (acc.contains("\r\n")) curr.utf8String else acc + curr.utf8String)
						.filter(_.contains("\r\n"))
						.map(json => Try(parse(json).extract[Tweet]))
						.runForeach {
							case Success(tweet) =>
								//Builder that makes a new MongoDB Object
								val builder = MongoDBObject.newBuilder
									builder += "name" -> tweet.user.name
									builder += "text" -> tweet.text
									builder += "count" -> count
								val newObj = builder.result
								//Save the new Object to the MongoDB database
								MongoFactory.collection.save(newObj)
								
								val publisher = system.actorSelection("/user/rabbitmq/publisher")
								def publish(channel: Channel) {
									channel.basicPublish(exchange, "", null, toBytes(count))
								}
								publisher ! ChannelMessage(publish, dropIfNoChannel = false)

								
								//Print the info in the terminal
								println("-----")
								println("tweeting from " + tweet.user.location)
								println(tweet.text)
								tweetBuffer += tweet;
								println(count + " There are now " + tweetBuffer.length + " tweets int the buffer")
								count = count + 1									   
							case Failure(e) =>

						}
				}
			}
		case Failure(failure) => println(failure.getMessage)
	}
}