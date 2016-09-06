package shield.actors.config.middleware

import akka.actor.{Actor, ActorLogging}
import shield.actors.{Middleware, RestartLogging}
import shield.actors.config.ConfigWatcherMsgs
import shield.actors.middleware.ResponseCache
import shield.config.DomainSettings


class ResponseCacheBuilder(id: String, domain: DomainSettings) extends Actor with ActorLogging with MiddlewareBuilder with RestartLogging {
  val c = domain.ConfigForMiddleware(id)

  log.info(s"Building ResponseCache '$id' with config $c")

  domain.MiddlewareChain.find(_.id == id) match {
    case None => log.warning(s"Could not find SLA for middleware $id")
    case Some(mw) =>
      context.parent ! ConfigWatcherMsgs.MiddlewareUpdated(Middleware(
        id,
        mw.sla,
        context.actorOf(ResponseCache.props(
          id,
          domain.KVStores(c.getString("kvstore")),
          settings.DefaultServiceLocation
        ))
      ))
  }

  def receive = {
    case _ =>
  }
}
