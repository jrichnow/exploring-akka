package com.framedobjects.auction

import akka.actor.FSM.{CurrentState, SubscribeTransitionCallBack, Transition}
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestFSMRef, TestKit}
import com.framedobjects.auction.Auction.{AuctionData, BiddingClosed, OpenForBidding, WinnerSelected}
import com.framedobjects.auction.AuctionProtocol._
import org.scalatest.{FunSpecLike, MustMatchers}

import scala.concurrent.duration._
import scala.language.postfixOps

class AuctionSpec extends TestKit(ActorSystem("auction-system")) with MustMatchers with FunSpecLike with ImplicitSender {

  describe("The Auction") {

    describe("in the OpenForBidding state") {

      it("should be in the OpenForBidding state on creation") {
        val auction = TestActorRef(Props(new Auction))
        auction ! SubscribeTransitionCallBack(testActor)

        expectMsg(CurrentState(auction, OpenForBidding))
      }

      it("should accept a bid in the OpenForBidding state") {
        val auction = TestActorRef(Props(new Auction))
        auction ! Bid(BigDecimal("1.50"))

        auction ! GetBidCount

        expectMsg(1)
      }

      it("should accept many bids in the OpenForBidding state") {
        val auction = TestFSMRef(new Auction)
        val totalMsgCount = 10
        for (i <- 1 to totalMsgCount) {
          auction ! Bid(BigDecimal("1.50"))
        }

        auction ! GetBidCount

        expectMsg(totalMsgCount)
      }

      it("should transition to the BiddingClosed state when receiving a CloseBidding event") {
        within(1 second) {
          val auction = TestFSMRef(new Auction)
          auction.stateName must equal(OpenForBidding)
          auction.stateData must be(AuctionData(List.empty, None))

          auction ! CloseBidding

          auction ! Bid(BigDecimal("1.50"))
          expectMsg(AuctionClosed)
        }
      }

      it("should transition to BiddingClosed state after timeout") {
        within(1 second) {
          val auction = TestFSMRef(new Auction)
          auction.isTimerActive("time-for-bidding") === true
          auction ! Bid(BigDecimal("1.50"))

          Thread.sleep(120)

          auction.stateName must equal(BiddingClosed)
          auction.isTimerActive("time-for-bidding") === false

          auction ! Bid(BigDecimal("1.50"))
          expectMsg(AuctionClosed)
        }
      }

      it("should not accept the GetWinner") {
        val auction = TestFSMRef(new Auction)

        auction ! GetWinner

        expectMsg(OpenForBidding)
      }
    }

    describe("in the BiddingClosed state") {

      it("should not allow to add more bids") {
        val auction = TestActorRef(Props(new Auction))
        auction ! CloseBidding
        auction ! Bid(BigDecimal("1.50"))

        expectMsg(AuctionClosed)
      }

      it("should determine the winner when receing a GetWinner event") {
        val auction = TestFSMRef(new Auction)
        auction ! Bid(BigDecimal("1.70"))
        auction ! Bid(BigDecimal("1.50"))
        auction ! CloseBidding
        auction.stateName must be(BiddingClosed)

        auction ! GetWinner

        expectMsg(Winner(BigDecimal("1.51"), Bid(BigDecimal("1.70"))))

        auction.stateName must be(WinnerSelected)
      }
    }

    describe("in the WinnerSelected state") {
      it("should not accept more bids") {
        val auction = TestFSMRef(new Auction)
        auction.setState(WinnerSelected, AuctionData(List.empty, None))

        auction ! Bid(BigDecimal("1.50"))

        expectMsg(AuctionClosed)
      }

      it("should return the picked winner") {
        val auction = TestFSMRef(new Auction)
        val winner: Winner = Winner(BigDecimal("1.51"), Bid(BigDecimal("1.70")))
        auction.setState(WinnerSelected, AuctionData(List.empty, Some(winner)))

        auction ! GetWinner

        expectMsg(winner)
      }
    }
  }
}