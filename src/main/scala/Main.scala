import java.io.{File, FilenameFilter}
import java.time.ZonedDateTime

import com.om.mxs.client.japi.{MatrixStore, UserInfo}
import helpers.UserInfoBuilder
import org.slf4j.LoggerFactory
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

object Main {
  private val logger = LoggerFactory.getLogger(getClass)
  lazy val vaultSettingsDir = sys.env.getOrElse("VAULT_INFORMATION_PATH", "/etc/vaults")
  lazy val elasticSearchUrl = sys.env.getOrElse("ELASTICSEARCH_URL","http://localhost:9200")
  lazy val indexName = sys.env.getOrElse("INDEX_NAME", "diskspace")
  lazy val sleepTimeMins = sys.env.getOrElse("SLEEP_TIME_MINS","5").toInt

  lazy val myHostName = java.net.InetAddress.getLocalHost.getHostName

  lazy val esDataDAO = new ESDataDAO(elasticSearchUrl, indexName)

  /**
    * shuts down the app in the case of a fatal error. Does not return.
    * @param exitCode exit code to return
    */
  private def terminate(exitCode:Int) = {
    System.exit(exitCode)
  }

  private val vaultFileFilter = new FilenameFilter {
    override def accept(dir: File, name: String): Boolean ={
      logger.warn(s"checking $name: ${name.endsWith(".vault")}")
      name.endsWith(".vault")
    }
  }
  /**
    * loads .vault information/login files from the specified directory and returns a map of
    * (ip address, vaultid)=>(userinfo). Userinfo entries with multiple addresses get multiple entries in the
    * returned map, one for each address each pointing to the same `UserInfo` instance.
    * @return Map of (String,UserInfo)
    */
  protected def loadInFiles():Map[String,UserInfo] = {
    logger.info(s"Loading configuration files from $vaultSettingsDir")

    val dir = new File(vaultSettingsDir)

    val files = dir.listFiles(vaultFileFilter)
    if(files==null){
      logger.error(s"Could not find directory $vaultSettingsDir to load vault settings from")
      terminate(2)
    }

    val maybeUserInfos = files.map(f=>{
      if(f.isFile) {
        logger.info(s"Loading login info from ${f.getAbsolutePath}")
        Some(UserInfoBuilder.fromFile(f).map(userInfo=>(f.getAbsolutePath,userInfo)))
      } else {
        None
      }
    }).collect({ case Some(entry)=>entry})

    val failures = maybeUserInfos.collect({case Failure(err)=>err})

    if(failures.nonEmpty){
      logger.error(s"${failures.length} out of ${maybeUserInfos.length} vault files failed to load: ")
      failures.foreach(err=>logger.error("Vault file failed: ", err))
    }

    val userInfos = maybeUserInfos.collect({case Success(info)=>info})

    if(userInfos.isEmpty){
      logger.error(s"Could not load any vault information files from $vaultSettingsDir, exiting app")
      terminate(2)
    }

    userInfos.foreach(info=>logger.debug(s"${info.toString}: ${info._2.getVault} on ${info._2.getAddresses.mkString(",")}"))
    logger.info(s"Loaded ${userInfos.length} vault information files")

    userInfos.toMap
  }

  def filenameOnly(fullPath:String):String = {
    val parts = fullPath.split(".+?/(?=[^/]+$)")
    if(parts==null) return fullPath

    if(parts.length>1){
      parts(1)
    } else {
      parts.head
    }
  }

  def gatherData(vaultInfos:Map[String,UserInfo]) = {
    logger.info("Gathering data...")
    val maybeDataPoints = vaultInfos.map(infoTuple=>{
      val vaultFile = filenameOnly(infoTuple._1)
      val userInfo = infoTuple._2
      try {
        val vault = MatrixStore.openVault(userInfo)

        try {
          val vaultAttribs = vault.getAttributes
          logger.info(s"Vault ${userInfo.getVault} on $vaultFile (${userInfo.getClusterId}): Usable space is ${ESData.reduceValue(vaultAttribs.usableSpace()/1000)} / ${ESData.reduceValue(vaultAttribs.totalSpace()/1000)}")
          val pct = 1.0 - (vaultAttribs.usableSpace().toDouble / vaultAttribs.totalSpace().toDouble)

          Some(ESData(vaultAttribs.usableSpace()/1000, (pct * 100).toInt, (vaultAttribs.totalSpace() - vaultAttribs.usableSpace())/1000, vaultFile, myHostName, ZonedDateTime.now()))
        } catch {
          case err: Throwable =>
            logger.error(s"Could not get vault attributes for $vaultFile: ", err)
            None
        } finally {
          //ensure that we always release the vault connection
          vault.dispose()
        }
      } catch {
        case err:Throwable=>
          logger.error(s"Could not access vault $vaultFile: ", err)
          None
      }
    })

    val failures = maybeDataPoints.count(_.isEmpty)
    if(failures>0){
      logger.warn(s"Failed to gather data for $failures vaults")
    }
    maybeDataPoints.collect({case Some(entry)=>entry}).toSeq
  }

  def main(args: Array[String]): Unit = {
    val vaultInfos = loadInFiles()

    while(true){
      def doPush() {
        val entries = gatherData(vaultInfos)
        entries.foreach(entry=>logger.info(s"\t$entry"))
        esDataDAO.pushEntries(entries).onComplete({
          case Failure(err)=>
            logger.error(s"Could not output to ElasticSearch at $elasticSearchUrl: ", err)
          case Success(response)=>
            if(response.isError){
              logger.error(s"Could not output to ElasticSearch at $elasticSearchUrl: ${response.error}")
            } else {
              logger.info(s"Wrote data to index")
            }
        })
      }
      doPush()  //it's done like this to try to make GC more effective
      logger.info("Data transmitted")
      System.gc()
      Thread.sleep(sleepTimeMins*60*1000)
    }
  }
}
