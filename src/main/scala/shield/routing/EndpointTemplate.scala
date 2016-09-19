package shield.routing

import io.swagger.models.Swagger
import spray.http.{HttpMethod, HttpRequest, MediaType}

object EndpointTemplate {
  def pseudo(request: HttpRequest) : EndpointTemplate = {
    EndpointTemplate(request.method, Path(request.uri.path.toString()))
  }
}
case class EndpointTemplate(method: HttpMethod, path: Path)

object EndpointDetails {
  def apply(params: Set[Param], canConsume: Set[MediaType], canProduce: Set[MediaType], disabledMiddleware: Set[String], disabledListeners: Set[String]): EndpointDetails = {
    EndpointDetails(params, canConsume, canProduce, disabledMiddleware, disabledListeners, new Swagger())
  }

  val empty = EndpointDetails(Set.empty, Set.empty, Set.empty, Set.empty, Set.empty, new Swagger())
}
case class EndpointDetails(params: Set[Param], canConsume: Set[MediaType], canProduce: Set[MediaType], disabledMiddleware: Set[String], disabledListeners: Set[String], swagger: Swagger)
