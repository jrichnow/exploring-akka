package com.framedobjects.auction

import akka.actor.FSM
import akka.util.Timeout
import com.framedobjects.auction.Auction.{BiddingClosed, _}
import com.framedobjects.auction.AuctionProtocol._
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.math.BigDecimal
import scala.language.postfixOps


class Auction extends FSM[AuctionState, AuctionData] {

  private val logger = LoggerFactory.getLogger(classOf[Auction])


  override def preStart() {
    setTimer("time-for-bidding", CloseBidding, 100 millis, false)
  }

  startWith(OpenForBidding, AuctionData(List.empty, None))

  when(OpenForBidding) {
    case Event(a: Bid, AuctionData(bids, _)) => {
      logger.info(s"received bid ${a.amount}")
      stay() using stateData.copy(bids :+ a)
    }
  }

  when(BiddingClosed) {
    case Event(Bid(amount), _) => {
      logger.info("Received bid after auction closed")
      sender ! AuctionClosed
      stay()
    }
    case Event(GetWinner, _) => {
      logger.info(s"calculating winner ....")
      val auctionData = determineWinner()
      sender ! auctionData.winner.get
      goto(WinnerSelected) using auctionData
    }
  }

  when(WinnerSelected) {
    case Event(a: Bid, _) => {
      logger.info(s"received bid ${a.amount}")
      sender ! AuctionClosed
      stay()
    }
    case Event(GetWinner, _) => {
      sender ! stateData.winner.get
      stay()
    }
  }

  whenUnhandled {
    case Event(CloseBidding, _) => goto(BiddingClosed)
    case Event(GetBidCount, _) => {
      sender ! stateData.bids.size
      stay()
    }
    case Event(GetWinner, _) => {
      sender ! stateName
      stay()
    }
  }

  onTransition {
    case OpenForBidding -> BiddingClosed => logger.debug("now closing bidding ...")
    case BiddingClosed -> WinnerSelected => logger.debug("winner was selected ...")
  }

  def determineWinner(): AuctionData = {
    val floorPrice = BigDecimal("0.50")

    if (stateData.bids.length > 0) {
      val sortedByAmount = stateData.bids.sortBy(_.amount).reverse
      sortedByAmount.foreach(println)
      val bid = sortedByAmount.head

      if (sortedByAmount.length > 1) {
        val secondBid = sortedByAmount.tail.head
        val winner = Winner(secondBid.amount + BigDecimal("0.01"), bid)
        stateData.copy(stateData.bids, Some(winner))
      }
      else {
        val winner = Winner(floorPrice, bid)
        stateData.copy(stateData.bids, Some(winner))
      }
    }
    else stateData
  }
}

object Auction {

  case class AuctionData(bids: List[Bid], winner: Option[Winner])

  sealed trait AuctionState
  case object OpenForBidding extends AuctionState
  case object BiddingClosed extends AuctionState
  case object WinnerSelected extends AuctionState
}

object AuctionProtocol {

  trait BidInteraction
  trait AuctionInteraction

  case class Bid(amount: BigDecimal) extends BidInteraction
  case class Winner(price: BigDecimal, bid: Bid)

  object CloseBidding extends AuctionInteraction
  object AuctionClosed extends AuctionInteraction
  object GetBidCount extends AuctionInteraction
  object GetWinner extends AuctionInteraction

}