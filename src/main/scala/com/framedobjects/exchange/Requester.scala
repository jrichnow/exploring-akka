package com.framedobjects.exchange

import akka.actor.{Actor, ActorLogging, Props}
import akka.util.Timeout
import com.framedobjects.exchange.Requester.ImpressionRequest

class Requester(implicit timeout: Timeout) extends Actor with ActorLogging {

  def receive = {
    case ImpressionRequest(sid) => {
      log.debug(s"received request for slot $sid")
      sender ! s"Hi got impression request for sid $sid"
    }
  }
}

object Requester {

  def props(implicit timeout: Timeout) = Props(new Requester)
  def name = "ImpressionRequesterActor"

  case class ImpressionRequest(sid: String)

  case class RequestData ()

  sealed trait BidResponse
}
