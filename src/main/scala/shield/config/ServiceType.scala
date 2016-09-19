package shield.config


import shield.swagger._
import spray.client.pipelining.SendReceive

import scala.concurrent.ExecutionContext

object ServiceType {
  val lookup : Map[String, ServiceType] = Map(
    Swagger2ServiceType.typeName -> Swagger2ServiceType,
    LambdaServiceType.typeName -> LambdaServiceType
  )
}

trait ServiceType {
  val typeName : String
  def fetcher(pipeline: SendReceive, settings: SettingsImpl)(implicit executor: ExecutionContext) : SwaggerFetcher
}

case object Swagger2ServiceType extends ServiceType {
  val typeName = "swagger2"
  def fetcher(pipeline: SendReceive, settings: SettingsImpl)(implicit executor: ExecutionContext) : SwaggerFetcher = new Swagger2Fetcher(settings.Swagger2Path, pipeline)
}

case object LambdaServiceType extends ServiceType {
  val typeName = "lambda"
  def fetcher(pipeline: SendReceive, settings: SettingsImpl)(implicit executor: ExecutionContext) : SwaggerFetcher = new Swagger2Fetcher(settings.Swagger2Path, pipeline)
}