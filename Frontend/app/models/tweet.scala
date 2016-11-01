package models

case class tweet(
	text: String,
	name: String
)

object JsonFormats {
  import play.api.libs.json.Json

  // Generates Writes and Reads for Feed and Tweet thanks to Json Macros
  implicit val tweetFormat = Json.format[tweet]
}

/*case class BoundingBox(coordinates: Seq[Seq[Seq[Float]]], `type`: String)
case class Contributor(id: Long, id_str: String, screen_name: String)
case class Coordinates(coordinates: Seq[Float], `type`: String)
case class Entities(hashtags: Seq[Hashtag], media: Seq[Media], urls: Seq[Url], user_mentions: Option[Seq[UserMention]])
case class Sizes(thumb: Size, large: Size, medium: Size, small: Size)
case class Size(h: Int, w: Int, resize: String)
case class Hashtag(indices: Seq[Int], text: String)
case class Url(display_url: String, expanded_url: String, indices: Seq[Int], url: String)
case class UserMention(id: Long, id_str: String, indices: Seq[Int], name: String, screen_name: String)*/