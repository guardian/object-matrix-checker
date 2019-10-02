import com.sksamuel.elastic4s.http.{ElasticClient, ElasticProperties}
import io.circe.generic.auto._
import io.circe.syntax._

class ESDataDAO(esUrl:String, indexName:String) extends ZonedDateTimeEncoder {
  import com.sksamuel.elastic4s.http.ElasticDsl._
  import com.sksamuel.elastic4s.circe._

  lazy val esClient = ElasticClient(ElasticProperties(esUrl))

  /**
    * bulk push the given list of entries to the index
    * @param entries sequence of entries to push
    * @return
    */
  def pushEntries(entries:Seq[ESData]) = esClient.execute {
    bulk(
      entries.map(entry=>indexInto(indexName / "diskspace") doc entry)
    )
  }
}
