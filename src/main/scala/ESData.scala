import java.time.ZonedDateTime

object ESData {
  private val suffices = Seq("bytes", "Kb", "Mb", "Gb", "Tb")

  def reduceValue(value:Long, iteration:Int=0):String = {
    if(value>1024 && iteration<suffices.length) {
      reduceValue(value/1024, iteration+1)
    } else {
      s"$value ${suffices(iteration)}"
    }
  }
}

case class ESData (Available:Long, Capacity: Int, Used:Long, Filesystem:String, Hostname: String, Timestamp:ZonedDateTime) {
  import ESData._

  override def toString: String = {
    s"$Filesystem on $Hostname: Used $Capacity % (${reduceValue(Available)} available)"
  }
}