import com.typesafe.sbt.packager.docker
import com.typesafe.sbt.packager.docker.{Cmd, DockerPermissionStrategy}

name := "ObjectMatrixChecker"

version := "1.0"

scalaVersion := "2.12.10"

val slf4jVersion = "1.7.25"
val elastic4sVersion = "6.5.1"

enablePlugins(DockerPlugin, AshScriptPlugin)

libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-api" % slf4jVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.sksamuel.elastic4s" %% "elastic4s-http" % elastic4sVersion,
  "com.sksamuel.elastic4s" %% "elastic4s-circe" % elastic4sVersion,
//  "com.sksamuel.elastic4s" %% "elastic4s-testkit" % elastic4sVersion % "test",
//  "com.sksamuel.elastic4s" %% "elastic4s-embedded" % elastic4sVersion % "test",
)

version := sys.props.getOrElse("build.number","DEV")
dockerPermissionStrategy := DockerPermissionStrategy.Run
daemonUserUid in Docker := None
daemonUser in Docker := "daemon"
dockerExposedPorts := Seq(9000)
dockerUsername  := sys.props.get("docker.username")
dockerRepository := Some("guardianmultimedia")
packageName in Docker := "guardianmultimedia/object-matrix-checker"
packageName := "object-matrix-checker"
dockerBaseImage := "openjdk:8-jdk-alpine"
dockerAlias := docker.DockerAlias(None,Some("guardianmultimedia"),"object-matrix-checker",Some(sys.props.getOrElse("build.number","DEV")))
dockerCommands ++= Seq(
  Cmd("USER","root"), //fix the permissions in the built docker image
  Cmd("RUN", "chown daemon /opt/docker"),
  Cmd("RUN", "chmod u+w /opt/docker"),
  Cmd("RUN", "chmod -R a+x /opt/docker"),
  Cmd("USER", "daemon")
)