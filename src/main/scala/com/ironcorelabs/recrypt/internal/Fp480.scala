/*
 * Copyright (C) 2017-present  IronCore Labs
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ironcorelabs.recrypt.internal

import cats.kernel.Eq
import scodec.bits.ByteVector
import spire.algebra.Field
import cats.syntax.either._

/**
 * Fp480 represents a value in the field of integers mod some Prime - that is, values in [0, Prime - 1].
 *
 * This is a pattern that is laid out by https://failex.blogspot.com/2017/04/and-glorious-subst-to-come.html
 * The idea is that `Fp480` is a type alias for some dependent type on a singleton "Impl.T", which
 * is just a subtype of BigInt.
 *
 * We get the guarantees about Fp480's safety by modding anytime someone calls the apply on the companion
 * object, but without any runtime allocations.
 */
final object Fp480 {
  def apply(x: BigInt): Impl.T = Impl(x)
  def apply(b: ByteVector): Impl.T = Impl(b)
  trait BigIntImpl {
    type T <: BigInt
    def apply(x: BigInt): T
    def apply(b: ByteVector): T
  }

  val Impl: BigIntImpl = new BigIntImpl {
    type T = BigInt
    def apply(x: BigInt): T = positiveMod(x, Prime)
    def apply(b: ByteVector): T = apply(byteVectorToBigInt(b))
  }
  //scalastyle:off
  final val Prime = BigInt("3121577065842246806003085452055281276803074876175537384188619957989004527066410274868798956582915008874704066849018213144375771284425395508176023")
  final val Order = BigInt("3121577065842246806003085452055281276803074876175537384188619957989004525299611739143164276204220965332554591187396064132658995685351714167608049")
  //scalastyle:on
  val Zero = Fp480(BigIntZero)
  val One = Fp480(BigIntOne)

  //The leading byte is a 0 since Prime is positive, we don't want that in all the sizes.
  val ExpectedFp480Length: Long = Prime.toByteArray.length - 1L
  val ExpectedOrderLength: Long = Order.toByteArray.length - 1L

  def bigIntToByteVector(b: BigInt): ByteVector = {
    val zeroByte = 0.toByte
    val byteVector = ByteVector.view(b.toByteArray)
    //Drop the leading zero if there is one (Because this value is positive.) Then make sure that the
    //byteVector is padded out to ExpectedFpLength
    byteVector.dropWhile(_ == zeroByte).padLeft(ExpectedFp480Length)
  }

  /**
   * inverse mod a prime is always in Fp480, so the cast is safe.
   */
  def inverseModPrime(fp: Fp480): Fp480 = inverse(fp, Prime).getOrElse(Fp480.Zero).asInstanceOf[Fp480]
  /**
   * Only call this if `i` is very close to the Prime, otherwise it will be inefficient.
   */
  @annotation.tailrec
  def fastModPrime(i: BigInt): Fp480 = {
    if (i >= Fp480.Prime) {
      fastModPrime(i - Prime)
    } else {
      Fp480(i)
    }
  }

  lazy val curvePoints: CurvePoints[Fp480.Impl.T] = {
    import implicits._
    //scalastyle:off
    CurvePoints(
      // Fixed point in cyclic group G1 (the trace zero subgroup).
      //   Start with a point that is on the twisted curve y^2 = x^3 + (3 / (u + 3)).
      //   Turns out u + 1 is a valid x, with y = sqrt(x^3 + (3 / (u + 3)).
      //   Take (x,y) and multiply by (p + p - r) to get an r-torsion element of the twisted curve over FP2.
      //   Compute the anti-trace map of that r-torsion element to get a point in the trace-zero subgroup.
      point.HomogeneousPoint[FP2Elem[Fp480]](
        FP2Elem(
          Fp480(BigInt("2836796539847730496121374298065944583953504150765508351672461175175719456840753019328265331693934514908570706456436537314841014056269083482678066")),
          Fp480(BigInt("2673768771775032355420306564841108930438651217980189126243896874678717443069132673614464990588309533043439946857284622768054880686771881306409642"))
        ),
        FP2Elem(
          Fp480(BigInt("3080607037190881313834826417530769563132997895310223092587326825944478280552006660025218522727539672756392754158719384720663526682905220416822282")),
          Fp480(BigInt("2906149584369018327289172171212098825323249826660713579814103802892067659387776431613853400363371122569649698984076618174478076051599518261188038"))
        ),
        FP2Elem(Zero, One)
      ),
      // Used to hash integers to a point in FP2
      // Generated by multiplying g1 by the SHA256 hash of the date/time "Mon Mar 26 13:33:43 MDT 2018\n",
      // encoded in ASCII/UTF-8, converted to a BigInt.
      point.HomogeneousPoint[FP2Elem[Fp480]](
        FP2Elem(
          Fp480(BigInt("2755895806273995492284787079187941247738254659153318682910850469784894541505170101984499629579667341065183727568252238059654392233777140703184622")),
          Fp480(BigInt("630223019374291956078632635377008181978279936792489208439110695275442238629433173084638817027401619964084048824893758705005177091915333122454106"))
        ),
        FP2Elem(
          Fp480(BigInt("659567418809487070027824196603118281636508923398279856378657485535700744304348227321440219246561941778839111505653577989859933696546799454574627")),
          Fp480(BigInt("2632696014493840722302529499863944843283552594579584896121087553911353425460849587377878627157950485322470437984154786925535244302963434645693127"))
        ),
        FP2Elem(
          Fp480(BigInt("2723878025330396352455760809227014699447653800671598495327573996671655085492645713095099955627015617726770647513458138760772123814185152132721554")),
          Fp480(BigInt("2520811096583992837832885181589663735998061148584558864774875442611283633943850563303274829680791759077833709816521531207043963496532513260928366"))
        )
      ),
      point.HomogeneousPoint(Fp480(1), Fp480(2), Fp480(1))
    )
    //scalastyle:on
  }

  //Object which contains the implicits for Fp480.
  final object implicits { // scalastyle:ignore object.name
    implicit val fp480Eq: Eq[Fp480.Impl.T] = Eq.fromUniversalEquals
    implicit val fieldForFp480: Field[Fp480.Impl.T] = new Field.WithDefaultGCD[Fp480] {
      //These casts are safe because they shouldn't ever produce something that is
      //not in Fp480. This is demonstrated in unit tests, but not statically.
      def negate(x: Fp480): Fp480 = if (x == Fp480.Zero) Fp480.Zero else (Fp480.Prime - x).asInstanceOf[Fp480]
      val zero: Fp480 = Fp480.Zero
      def plus(x: Fp480, y: Fp480): Fp480 = fastModPrime(x + y)
      def times(x: Fp480, y: Fp480): Fp480 = Fp480(x * y)
      def div(x: Fp480, y: Fp480): Fp480 = Fp480(x * Fp480.inverseModPrime(y))
      override def reciprocal(fp: Fp480): Fp480 = Fp480.inverseModPrime(fp)
      val one: Fp480 = Fp480.One
    }

    implicit val modsByPrimeFp480: ModsByPrime[Fp480.Impl.T] = new ModsByPrime[Fp480] {
      def create(i: BigInt): Fp480 = Fp480(i)
      def create(b: ByteVector): Fp480 = Fp480(b)
      val prime: BigInt = Fp480.Prime
    }

    implicit val extensionFieldFp480: ExtensionField[Fp480.Impl.T] = new ExtensionField[Fp480.Impl.T] {
      val xi: FP2Elem[Fp480.Impl.T] = FP2Elem(Fp480(1), Fp480(3))
      //scalastyle:off
      val frobeniusFactor1: FP2Elem[Fp480.Impl.T] = FP2Elem(
        Fp480(BigInt("2705020609406098470693743943193507017690525853579041639836321147125100162418094245778443957282985233325521741487078451689773015537700623376387510")),
        Fp480(BigInt("1651643729828744562959031609260204931467006255025965356538853937438900508750440674159520451455470865884696804132950675577710427706655106873786415"))
      )
      val frobeniusFactor2: FP2Elem[Fp480.Impl.T] = FP2Elem(
        Fp480(BigInt("2306651261022207350847683647334036061609898996050387019709069937614457385067216464366007100887697910705559503143400341307341852524867445116042081")),
        Fp480(BigInt("2700715513864156317217817646762981219394581764002749630356081821581587105847754189716578562648186994801551242072586877237522675959303129100251619"))
      )
      val frobeniusFactorFp12: FP2Elem[Fp480.Impl.T] = FP2Elem(
        Fp480(BigInt("1656507924366244928424688705439191250492553228839737584554076474712325077234544758394965182643615114744030751122897661836350026981040666763359635")),
        Fp480(BigInt("411347727129503104504468123876138850111359831064167067331474757563790630947112631916366442374487601276802707186517326847256605909760387475785136"))
      )
      //scalastyle:on
      /**
       * v is the thing that cubes to xi
       * v^3 = u+3, because by definition it is a solution to the equation y^3 - (u + 3)
       */
      def v: FP6Elem[Fp480.Impl.T] = FP6Elem(Field[FP2Elem[Fp480]].zero, Field[FP2Elem[Fp480]].one, Field[FP2Elem[Fp480]].zero)
    }

    implicit val pairingConfigFp480: PairingConfig[Fp480.Impl.T] = new PairingConfig[Fp480.Impl.T] {
      //NAF of 6*BNParam + 2
      private[this] val NAF = IndexedSeq(0, 0, 0, -1, 0, 1, 0, 0, 0, -1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, -1, 0, 1, 0, 1, 0, -1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 1, 0, 0, 0, -1, 0, 0, -1, 0, 0, 0, 1, 0, 0, 0, 1, 0, -1, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0, 1, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, -1, 0, 0, 1, 0, 0, -1, 0, 0, 0, 0, 1, 0, 0, 0, -1, 0, 1, 0, -1, 0, 0, 1, 0, 1, 0) //scalastyle:ignore
      // The reason we drop(2) is because of Algorithm 1 in High-Speed Software Implementation of the Optimal Ate Pairing over Barreto–Naehrig Curves
      val nafForLoop: IndexedSeq[Int] = NAF.reverse.drop(2)
      //This is based on the BNParam. It's the cuberoot of BNParam. It should always be called
      //in multiples of 3.
      def bnPow(self: FP12Elem[Fp480.Impl.T]): FP12Elem[Fp480.Impl.T] = {
        //This is a hardcode of the square and multiply for bnPow
        var x = self
        var res = x
        1.to(3).foreach(_ => x = square(x))
        res = res * x
        1.to(4).foreach(_ => x = square(x))
        res = res * x
        1.to(2).foreach(_ => x = square(x))
        res = res * x
        1.to(4).foreach(_ => x = square(x))
        res = res * x
        1.to(5).foreach(_ => x = square(x))
        res = res * x
        1.to(4).foreach(_ => x = square(x))
        res = res * x.conjugate
        1.to(2).foreach(_ => x = square(x))
        res = res * x
        1.to(3).foreach(_ => x = square(x))
        res = res * x
        1.to(2).foreach(_ => x = square(x))
        res = res * x.conjugate
        1.to(4).foreach(_ => x = square(x))
        res = res * x.conjugate
        1.to(5).foreach(_ => x = square(x))
        res = res * x.conjugate
        1.to(2).foreach(_ => x = square(x))
        res * x
      }
    }

    implicit val bytesDecoderFp480: BytesDecoder[Fp480.Impl.T] = BytesDecoder.forSize(ExpectedFp480Length.toInt)(Fp480(_).asRight)
    implicit val hashableFp480: Hashable[Fp480] = Hashable.by(bigIntToByteVector)
  }

}
