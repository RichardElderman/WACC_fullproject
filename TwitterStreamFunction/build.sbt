name := "Akka-Twitter-Streamer"

version := "1.0"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
	"com.typesafe.akka" %% "akka-stream-experimental" % "1.0",
	"com.typesafe.akka" %% "akka-http-experimental" % "1.0",
	"com.hunorkovacs" %% "koauth" % "1.1.0",
	"org.json4s" %% "json4s-native" % "3.3.0",
	"org.reactivemongo" %% "reactivemongo" % "0.10.5.0.akka23",
	"org.mongodb" %% "casbah" % "3.1.1",
	"org.slf4j" % "slf4j-api" % "1.7.5",
    "org.slf4j" % "slf4j-simple" % "1.7.5",
	"com.thenewmotion.akka" %% "akka-rabbitmq" % "2.3"
)

resolvers += "Typesafe" at "https://repo.typesafe.com/typesafe/releases/"
resolvers += "The New Motion Public Repo" at "http://nexus.thenewmotion.com/content/groups/public/"

