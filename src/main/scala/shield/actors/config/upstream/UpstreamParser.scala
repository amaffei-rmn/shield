package shield.actors.config.upstream

import shield.actors.config.ServiceDetails
import shield.config._
import spray.http.Uri

trait UpstreamParser {
  def parseUpstreamEntry(serviceType: String, serviceLocation: String, weight: Int) = {
    ServiceType.lookup(serviceType) match {
      case Swagger2ServiceType => HttpServiceLocation(Uri(serviceLocation)) -> ServiceDetails(Swagger2ServiceType, weight)
      case LambdaServiceType => LambdaServiceLocation(serviceLocation) -> ServiceDetails(LambdaServiceType, weight)
    }
  }
}