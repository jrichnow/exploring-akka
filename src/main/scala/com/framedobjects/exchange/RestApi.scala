package com.framedobjects.exchange

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import com.framedobjects.exchange.Requester.{ImpressionRequest, RequestData}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._

import scala.concurrent.ExecutionContext

class RestApi(system: ActorSystem, timeout: Timeout) extends RestRoutes {

  implicit val requestTimeout = timeout

  implicit def executionContext = system.dispatcher

  def createRequester = system.actorOf(Requester.props, Requester.name)

}


trait RestRoutes extends RequesterApi {

  import StatusCodes._

  def routes: Route = impressRequestRoute

  def impressRequestRoute =
    pathPrefix("impr") {
      pathEndOrSingleSlash {
        get {
          parameters('sid) { sid =>
            onSuccess(impressionRequest(sid)) {
              response => complete(OK, response)
            }
          }
        }
      }
    }
}

trait RequesterApi {

  import Requester._

  def createRequester(): ActorRef

  implicit def executionContext: ExecutionContext

  implicit def requestTimeout: Timeout

  lazy val requester = createRequester()

  def impressionRequest(sid: String) = requester.ask(ImpressionRequest(sid:String)).mapTo[String]
}