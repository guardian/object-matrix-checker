import java.time.ZonedDateTime

object ESData {
  private val suffices = Seq("Kb", "Mb", "Gb", "Tb")

  def reduceValue(value:Long, iteration:Int=0):String = {
    if(value>1024 && iteration<suffices.length) {
      reduceValue(value/1024, iteration+1)
    } else {
      s"$value ${suffices(iteration)}"
    }
  }
}

/**
  * represents a datapoint in the index
  * @param Available Kb available at this point. NOTE: KB, not Bytes!
  * @param Capacity Percentage capacity used, expressed as an integer (e.g., 83 => 83% used)
  * @param Used      Kb in use at this point
  * @param Filesystem Filesystem name
  * @param Hostname   Host that this was measured on
  * @param Timestamp  Time at which the measurement was taken
  */
case class ESData (Available:Long, Capacity: Int, Used:Long, Filesystem:String, Hostname: String, Timestamp:ZonedDateTime) {
  import ESData._

  override def toString: String = {
    s"$Filesystem on $Hostname: Used $Capacity % (${reduceValue(Available)} available)"
  }
}