package jp.ponko2.android.webime

import scala.xml._
import dispatch._
import dispatch.liftjson.Js._
import net.liftweb.json.JsonAST._
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

abstract class WebIME {
  val tag:String
  val api:Request
  def transliterate(text:String):Handler[Seq[String]]
}

object SocialIME extends WebIME {
  val tag = "Social IME"
  val api = :/("www.social-ime.com") / "api"

  def transliterate(text:String) = {
    require(text.nonEmpty)

    api <<? Map("charset" -> "UTF-8", "string" -> text, "resize[0]" -> "+99999") >- {
      tsv => tsv.stripLineEnd.split("[\t\n\r]+")
    }
  }
}

object GoogleJapaneseInput extends WebIME {
  val tag = "Google Japanese Input"
  val api = :/("www.google.com") / "transliterate"

  def transliterate(text:String) = {
    require(text.nonEmpty)

    api <<? Map("langpair" -> "ja-Hira|ja", "text" -> (text + ",")) ># {
      js => for(JArray(list) <- js; JString(data) <- list) yield data
    }
  }
}

object GoogleSuggest extends WebIME {
  val tag = "Google Suggest"
  val api = :/("www.google.com") / "complete" / "search"

  def transliterate(text:String) = {
    require(text.nonEmpty)

    api <<? Map("hl" -> "ja", "output" -> "toolbar", "q" -> text) >- {
      xml => {
        // Android2.1 対応
        val factory = SAXParserFactory.newInstance()
        factory.setFeature("http://xml.org/sax/features/namespaces", false)
        factory.setFeature("http://xml.org/sax/features/namespace-prefixes", true)
        val parser = factory.newSAXParser()

        for {
          suggestion <- XML.loadXML(new InputSource(new StringReader(xml)), parser) \\ "suggestion"
          data <- suggestion \ "@data" map(attribute => attribute.text)
        } yield data
      }
    }
  }
}
