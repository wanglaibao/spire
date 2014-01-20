package spire.math

/**
 * Approx[A, B] represents an ability to approximate As as Bs.
 * 
 * Successful calls to approximate() are expected to return an
 * "approximate" result. This is not yet precisely-defined, but at a
 * minimum if the given value exceed's B's range the approximation is
 * expected to fail.
 * 
 * Successful results provide a B as well as a Rational error
 * estimate.
 */
trait Approx[A, B] { self =>

  def approximate(a: A): Option[B]

  def canApproximate(a: A): Boolean =
    approximate(a).isDefined

  def compose[D](that: Approx[D, A]): Approx[D, B] =
    that andThen this

  def mapFrom[D](f: D => A): Approx[D, B] = new Approx[D, B] {
    def approximate(d: D): Option[B] = self.approximate(f(d))
  }

  def flatMapFrom[D](f: D => Option[A]): Approx[D, B] = new Approx[D, B] {
    def approximate(d: D): Option[B] = f(d).flatMap(self.approximate)
  }

  def andThen[C](that: Approx[B, C]): Approx[A, C] = new Approx[A, C] {
    def approximate(a: A): Option[C] = self.approximate(a).flatMap(that.approximate)
  }

  def map[C](f: B => C): Approx[A, C] = new Approx[A, C] {
    def approximate(a: A): Option[C] = self.approximate(a).map(f)
  }

  def flatMap[C](f: B => Option[C]): Approx[A, C] = new Approx[A, C] {
    def approximate(a: A): Option[C] = self.approximate(a).flatMap(b => f(b))
  }
}

/**
 * Coercion[A, B] represents an ability to convert some As to Bs.
 * 
 * Successful calls to coerce() are expected to exactly represent
 * the given value in B.
 * 
 * if canConvert(a1), and coerce(a1) = coerce(a2) then a1 = a2.
 */
trait Coercion[A, B] extends Approx[A, B] { self =>

  def coerce(a: A): Option[B]

  def canConvert(a: A): Boolean =
    coerce(a).isDefined

  def approximate(a: A): Option[B] = coerce(a)

  def convertOrSkip(as: Iterable[A]): Iterable[B] =
    as.flatMap(coerce)

  def convertOrDefault(as: Iterable[A])(default: B): Iterable[B] =
    as.map(a => coerce(a).getOrElse(default))

  def compose[D](that: Coercion[D, A]): Coercion[D, B] =
    that andThen this

  override def mapFrom[D](f: D => A): Coercion[D, B] = new Coercion[D, B] {
    def coerce(d: D): Option[B] = self.coerce(f(d))
  }

  override def flatMapFrom[D](f: D => Option[A]): Coercion[D, B] = new Coercion[D, B] {
    def coerce(d: D): Option[B] = f(d).flatMap(self.coerce)
  }

  def andThen[C](that: Coercion[B, C]): Coercion[A, C] = new Coercion[A, C] {
    def coerce(a: A): Option[C] = self.coerce(a).flatMap(that.coerce)
  }

  override def map[C](f: B => C): Coercion[A, C] = new Coercion[A, C] {
    def coerce(a: A): Option[C] = self.coerce(a).map(f)
  }

  override def flatMap[C](f: B => Option[C]): Coercion[A, C] = new Coercion[A, C] {
    def coerce(a: A): Option[C] = self.coerce(a).flatMap(f)
  }
}

/**
 * Conversion[A, B] represents an ability to convert all As to Bs.
 * 
 * Calls to convert() must always succeed, and are expected to exactly
 * represent the given value in B. This also means that an instance of
 * Conversion[A, B] provides a Coercion[A, B] that always succeeds, as
 * well as an (exact) Approx[A, B] that also always succeeds.
 * 
 * if convert(a1) = convert(a2) then a1 = a2.
 * 
 * Conversion[A, B] is an injection from A to B.
 */
trait Conversion[A, B] extends Coercion[A, B] with Approx[A, B] { self =>

  def convert(a: A): B

  def coerce(a: A): Some[B] = Some(convert(a))

  override def canConvert(a: A): Boolean = true

  def convertAll(as: Iterable[A]): Iterable[B] =
    as.map(convert)

  override def convertOrSkip(as: Iterable[A]): Iterable[B] =
    convertAll(as)

  override def convertOrDefault(as: Iterable[A])(default: B): Iterable[B] =
    convertAll(as)

  def compose[D](that: Conversion[D, A]): Conversion[D, B] =
    that andThen this

  override def mapFrom[D](f: D => A): Conversion[D, B] = new Conversion[D, B] {
    def convert(d: D): B = self.convert(f(d))
  }

  def andThen[C](that: Conversion[B, C]): Conversion[A, C] = new Conversion[A, C] {
    def convert(a: A): C = that.convert(self.convert(a))
  }

  override def map[C](f: B => C): Conversion[A, C] = new Conversion[A, C] {
    def convert(a: A): C = f(self.convert(a))
  }
}

class ConversionOps[A](a: A) {
  def convert[B](implicit ev: Conversion[A, B]): B = ev.convert(a)
  def coerce[B](implicit ev: Coercion[A, B]): Option[B] = ev.coerce(a)
}

object Conversion {
  def apply[A, B](f: A => B): Conversion[A, B] = new Conversion[A, B] {
    def convert(a: A): B = f(a)
  }
}

object Coercion {
  def apply[A, B](f: A => Option[B]): Coercion[A, B] = new Coercion[A, B] {
    def coerce(a: A): Option[B] = f(a)
  }
}

object Approx {
  implicit def noop[A] = new Conversion[A, A] { def convert(a: A): A = a }

  def apply[A, B](f: A => Option[B]): Approx[A, B] = new Approx[A, B] {
    def approximate(a: A): Option[B] = f(a)
  }

  import scala.reflect.ClassTag
  import spire.algebra.{Eq, Order, Semiring, Signed, AdditiveMonoid}
  import spire.math.poly.Term
  import spire.syntax.std.seq._
  
  private[spire] def maybeList[A](mas: List[Option[A]]): Option[List[A]] = {
    def loop(acc: List[A], mas: List[Option[A]]): Option[List[A]] = mas match {
      case Nil => Some(acc.reverse)
      case None :: _ => None
      case Some(a) :: tail => loop(a :: acc, tail)
    }
    loop(Nil, mas)
  }

  // convert to Complex[_]
  implicit def RealToComplex1[A, B](implicit ev: Conversion[A, B], f: Semiring[B]) = ev.map(b => Complex(b))
  implicit def RealToComplex2[A, B](implicit ev: Coercion[A, B], f: Semiring[B]) = ev.map(b => Complex(b))
  implicit def RealToComplex3[A, B](implicit ev: Approx[A, B], f: Semiring[B]) = ev.map(b => Complex(b))

  implicit def ComplexToComplex1[A, B](implicit ev: Conversion[A, B], f: Semiring[B]) =
    Conversion((a: Complex[A]) => Complex(ev.convert(a.real), ev.convert(a.imag)))
  implicit def ComplexToComplex2[A, B](implicit ev: Coercion[A, B], f: Semiring[B]) =
    Coercion((a: Complex[A]) => ev.coerce(a.real).flatMap(r => ev.coerce(a.imag).map(i => Complex(r, i))))
  implicit def ComplexToComplex3[A, B](implicit ev: Approx[A, B], f: Semiring[B]) =
    Approx((a: Complex[A]) => ev.approximate(a.real).flatMap(r => ev.approximate(a.imag).map(i => Complex(r, i))))

  implicit def QuaternionToComplex2[A, B](implicit ev: Coercion[A, B], f: Semiring[B], s: Signed[A]) =
    new Coercion[Quaternion[A], Complex[B]] {
      def coerce(a: Quaternion[A]): Option[Complex[B]] =
        if (s.signum(a.j) != 0 || s.signum(a.k) != 0) None
        else for {
          r <- ev.coerce(a.r)
          i <- ev.coerce(a.i)
        } yield Complex(r, i)
    }

  implicit def QuaternionToComplex3[A, B](implicit ev: Approx[A, B], f: Semiring[B], s: Signed[A]) =
    new Approx[Quaternion[A], Complex[B]] {
      def approximate(a: Quaternion[A]): Option[Complex[B]] =
        if (s.signum(a.j) != 0 || s.signum(a.k) != 0) None
        else for {
          r <- ev.approximate(a.r)
          i <- ev.approximate(a.i)
        } yield Complex(r, i)
    }
  
  // convert to Quaternion[_]
  implicit def RealToQuaternion1[A, B](implicit ev: Conversion[A, B], f: Semiring[B]) =
    ev.map(a => Quaternion(a))
  implicit def RealToQuaternion2[A, B](implicit ev: Coercion[A, B], f: Semiring[B]) =
    ev.map(a => Quaternion(a))
  implicit def RealToQuaternion3[A, B](implicit ev: Approx[A, B], f: Semiring[B]) =
    ev.map(a => Quaternion(a))

  implicit def ComplexToQuaternion1[A, B](implicit ev: Conversion[A, B], f: Semiring[B]) =
    Conversion((a: Complex[A]) => Quaternion(ev.convert(a.real), ev.convert(a.imag)))
  implicit def ComplexToQuaternion2[A, B](implicit ev: Coercion[A, B], f: Semiring[B]) =
    Coercion((a: Complex[A]) => ev.coerce(a.real).flatMap(r => ev.coerce(a.imag).map(i => Quaternion(r, i))))
  implicit def ComplexToQuaternion3[A, B](implicit ev: Approx[A, B], f: Semiring[B]) =
    Approx((a: Complex[A]) => ev.approximate(a.real).flatMap(r => ev.approximate(a.imag).map(i => Quaternion(r, i))))

  implicit def QuaternionToQuaternion1[A, B](implicit ev: Conversion[A, B], f: Semiring[B]) =
    Conversion((a: Quaternion[A]) => Quaternion(ev.convert(a.r), ev.convert(a.i), ev.convert(a.j), ev.convert(a.k)))
  implicit def QuaternionToQuaternion2[A, B](implicit ev: Coercion[A, B], f: Semiring[B]) =
    Coercion((a: Quaternion[A]) => for {
      r <- ev.coerce(a.r)
      i <- ev.coerce(a.i)
      j <- ev.coerce(a.j)
      k <- ev.coerce(a.k)
    } yield Quaternion(r, i, j, k))
  implicit def QuaternionToQuaternion3[A, B](implicit ev: Approx[A, B], f: Semiring[B]) =
    Approx((a: Quaternion[A]) => for {
      r <- ev.approximate(a.r)
      i <- ev.approximate(a.i)
      j <- ev.approximate(a.j)
      k <- ev.approximate(a.k)
    } yield Quaternion(r, i, j, k))
  
  // convert to Interval[_]
  implicit def RealToInterval1[A, B](implicit ev: Conversion[A, B], o: Order[B]) = ev.map(b => Interval.point(b))
  implicit def RealToInterval2[A, B](implicit ev: Coercion[A, B], o: Order[B]) = ev.map(b => Interval.point(b))
  implicit def RealToInterval3[A, B](implicit ev: Approx[A, B], o: Order[B]) = ev.map(b => Interval.point(b))
  
  implicit def IntervalToInterval1[A, B](implicit ev: Conversion[A, B], f: AdditiveMonoid[B], o: Order[B]) =
    Conversion((a: Interval[A]) => a.mapBounds(ev.convert))
  implicit def IntervalToInterval2[A, B](implicit ev: Coercion[A, B], f: AdditiveMonoid[B], o: Order[B]) =
    Conversion((a: Interval[A]) => for {
      b1 <- Interval.Bound.lift(a.lowerBound.map(ev.coerce))
      b2 <- Interval.Bound.lift(a.upperBound.map(ev.coerce))
    } yield Interval.fromBounds(b1, b2))
  implicit def IntervalToInterval3[A, B](implicit ev: Approx[A, B], f: AdditiveMonoid[B], o: Order[B]) =
    Conversion((a: Interval[A]) => for {
      b1 <- Interval.Bound.lift(a.lowerBound.map(ev.approximate))
      b2 <- Interval.Bound.lift(a.upperBound.map(ev.approximate))
    } yield Interval.fromBounds(b1, b2))
  
  // convert to Polynomial[_]
  implicit def RealToPolynomial1[A, B](implicit ev: Conversion[A, B], e: Eq[B], f: Semiring[B], ct: ClassTag[B]) =
    ev.map(b => Polynomial.constant(b))
  implicit def RealToPolynomial2[A, B](implicit ev: Coercion[A, B], e: Eq[B], f: Semiring[B], ct: ClassTag[B]) =
    ev.map(b => Polynomial.constant(b))
  implicit def RealToPolynomial3[A, B](implicit ev: Approx[A, B], e: Eq[B], f: Semiring[B], ct: ClassTag[B]) =
    ev.map(b => Polynomial.constant(b))
  
  implicit def PolynomialToPolynomial1[A, B](implicit ev: Conversion[A, B], ea: Eq[A], eb: Eq[B], fa: Semiring[A], fb: Semiring[B], ct: ClassTag[B]) =
    Conversion((a: Polynomial[A]) => Polynomial(a.terms.map { case Term(c, e) => Term(ev.convert(c), e) }))

  implicit def PolynomialToPolynomial2[A, B](implicit ev: Coercion[A, B], ea: Eq[A], eb: Eq[B], fa: Semiring[A], fb: Semiring[B], ct: ClassTag[B]) =
    Coercion((a: Polynomial[A]) => for {
      ts <- maybeList(a.terms.map { case Term(c, e) =>
        ev.coerce(c).map(b => Term(b, e))
      })
    } yield Polynomial(ts))

  implicit def PolynomialToPolynomial3[A, B](implicit ev: Approx[A, B], ea: Eq[A], eb: Eq[B], fa: Semiring[A], fb: Semiring[B], ct: ClassTag[B]) =
    Approx((a: Polynomial[A]) => for {
      ts <- maybeList(a.terms.map { case Term(c, e) =>
        ev.approximate(c).map(b => Term(b, e))
      })
    } yield Polynomial(ts))
  
  // UByte conversions
  implicit val UByteToByte = Coercion((n: UByte) => if (n.signed >= 0) Some(n.toByte) else None)

  implicit val UByteToUShort = Conversion((n: UByte) => UShort(n.toInt))
  implicit val UByteToUInt = Conversion((n: UByte) => UInt(n.toInt))
  implicit val UByteToULong = Conversion((n: UByte) => ULong(n.toInt))
  implicit val UByteToNatural = Conversion((n: UByte) => Natural(n.toInt))
  implicit val UByteToShort = Conversion((n: UByte) => n.toShort)
  implicit val UByteToInt = Conversion((n: UByte) => n.toInt)
  implicit val UByteToLong = Conversion((n: UByte) => n.toLong)
  implicit val UByteToBigInt = Conversion((n: UByte) => n.toBigInt)
  implicit val UByteToSafeLong = Conversion((n: UByte) => SafeLong(n.toInt))
  implicit val UByteToFloat = Conversion((n: UByte) => n.toFloat)
  implicit val UByteToDouble = Conversion((n: UByte) => n.toDouble)
  implicit val UByteToBigDecimal = Conversion((n: UByte) => BigDecimal(n.toInt))
  implicit val UByteToRational = Conversion((n: UByte) => Rational(n.toInt))
  implicit val UByteToAlgebraic = Conversion((n: UByte) => Algebraic(n.toInt))
  implicit val UByteToReal = Conversion((n: UByte) => Real(n.toInt))
  
  // UShort conversions
  implicit val UShortToUByte = Coercion((n: UShort) => if (n < UShort(256)) Some(UByte(n.signed)) else None)
  implicit val UShortToByte = Coercion((n: UShort) => if (n < UShort(128)) Some(n.toByte) else None)
  implicit val UShortToShort = Coercion((n: UShort) => if (n.signed >= 0) Some(n.toShort) else None)

  implicit val UShortToUInt = Conversion((n: UShort) => UInt(n.toInt))
  implicit val UShortToULong = Conversion((n: UShort) => ULong(n.toInt))
  implicit val UShortToNatural = Conversion((n: UShort) => Natural(n.toInt))
  implicit val UShortToInt = Conversion((n: UShort) => n.toInt)
  implicit val UShortToLong = Conversion((n: UShort) => n.toLong)
  implicit val UShortToBigInt = Conversion((n: UShort) => n.toBigInt)
  implicit val UShortToSafeLong = Conversion((n: UShort) => SafeLong(n.toInt))
  implicit val UShortToFloat = Conversion((n: UShort) => n.toFloat)
  implicit val UShortToDouble = Conversion((n: UShort) => n.toDouble)
  implicit val UShortToBigDecimal = Conversion((n: UShort) => BigDecimal(n.toInt))
  implicit val UShortToRational = Conversion((n: UShort) => Rational(n.toInt))
  implicit val UShortToAlgebraic = Conversion((n: UShort) => Algebraic(n.toInt))
  implicit val UShortToReal = Conversion((n: UShort) => Real(n.toInt))
  
  // UInt conversions
  implicit val UIntToUByte = Coercion((n: UInt) => if (n < UInt(256)) Some(UByte(n.signed)) else None)
  implicit val UIntToUShort = Coercion((n: UInt) => if (n < UInt(65536)) Some(UShort(n.signed)) else None)
  implicit val UIntToByte = Coercion((n: UInt) => if (n <= UInt(Byte.MaxValue)) Some(n.toByte) else None)
  implicit val UIntToShort = Coercion((n: UInt) => if (n <= UInt(Short.MaxValue)) Some(n.toShort) else None)
  implicit val UIntToInt = Coercion((n: UInt) => if (n.signed >= 0) Some(n.toInt) else None)
  implicit val UIntToFloat = Approx((n: UInt) => Some(n.toFloat))

  implicit val UIntToULong = Conversion((n: UInt) => ULong(n.toLong))
  implicit val UIntToNatural = Conversion((n: UInt) => Natural(n.toLong))
  implicit val UIntToLong = Conversion((n: UInt) => n.toLong)
  implicit val UIntToBigInt = Conversion((n: UInt) => n.toBigInt)
  implicit val UIntToSafeLong = Conversion((n: UInt) => SafeLong(n.toLong))
  implicit val UIntToDouble = Conversion((n: UInt) => n.toDouble)
  implicit val UIntToBigDecimal = Conversion((n: UInt) => BigDecimal(n.toLong))
  implicit val UIntToRational = Conversion((n: UInt) => Rational(n.toLong))
  implicit val UIntToAlgebraic = Conversion((n: UInt) => Algebraic(n.toLong))
  implicit val UIntToReal = Conversion((n: UInt) => Real(n.toLong))
  
  // ULong conversions
  implicit val ULongToUByte = Coercion((n: ULong) => if (n < ULong(256)) Some(UByte(n.signed.toInt)) else None)
  implicit val ULongToUShort = Coercion((n: ULong) => if (n < ULong(65536)) Some(UShort(n.signed.toInt)) else None)
  implicit val ULongToUInt = Coercion((n: ULong) => if (n < ULong(4294967296L)) Some(UInt(n.signed.toInt)) else None)
  implicit val ULongToByte = Coercion((n: ULong) => if (n <= ULong(Byte.MaxValue)) Some(n.toByte) else None)
  implicit val ULongToShort = Coercion((n: ULong) => if (n <= ULong(Short.MaxValue)) Some(n.toShort) else None)
  implicit val ULongToInt = Coercion((n: ULong) => if (n <= ULong(Int.MaxValue)) Some(n.toInt) else None)
  implicit val ULongToLong = Coercion((n: ULong) => if (n.signed >= 0) Some(n.toLong) else None)
  implicit val ULongToFloat = Approx((n: ULong) => Some(n.toFloat))
  implicit val ULongToDouble = Approx((n: ULong) => Some(n.toDouble))

  implicit val ULongToNatural = Conversion((n: ULong) => n.toNatural)
  implicit val ULongToBigInt = Conversion((n: ULong) => n.toBigInt)
  implicit val ULongToSafeLong = Conversion((n: ULong) => n.toSafeLong)
  implicit val ULongToBigDecimal = Conversion((n: ULong) => BigDecimal(n.toBigInt))
  implicit val ULongToRational = Conversion((n: ULong) => Rational(n.toSafeLong))
  implicit val ULongToAlgebraic = Conversion((n: ULong) => Algebraic(n.toSafeLong))
  implicit val ULongToReal = Conversion((n: ULong) => Real(n.toSafeLong))
  
  // Natural conversions
  implicit val NaturalToUByte = Coercion((n: Natural) => if (n < UInt(256)) Some(UByte(n.toInt)) else None)
  implicit val NaturalToUShort = Coercion((n: Natural) => if (n < UInt(65536)) Some(UShort(n.toInt)) else None)
  implicit val NaturalToUInt = Coercion((n: Natural) => if (n <= UInt.MaxValue) Some(n.toUInt) else None)
  implicit val NaturalToULong = Coercion((n: Natural) => if (n <= Natural(-1L)) Some(n.toULong) else None)
  implicit val NaturalToByte = Coercion((n: Natural) => if (n <= UInt(Byte.MaxValue)) Some(n.toByte) else None)
  implicit val NaturalToShort = Coercion((n: Natural) => if (n <= UInt(Short.MaxValue)) Some(n.toShort) else None)
  implicit val NaturalToInt = Coercion((n: Natural) => if (n <= UInt(Int.MaxValue)) Some(n.toInt) else None)
  implicit val NaturalToLong = Coercion((n: Natural) => if (n <= Natural(Long.MaxValue)) Some(n.toLong) else None)
  implicit val NaturalToFloat = Approx((n: Natural) => Some(n.toFloat)) //fixme
  implicit val NaturalToDouble = Approx((n: Natural) => Some(n.toDouble)) //fixme

  implicit val NaturalToBigInt = Conversion((n: Natural) => n.toBigInt)
  implicit val NaturalToSafeLong = Conversion((n: Natural) => SafeLong(n.toBigInt))
  implicit val NaturalToBigDecimal = Conversion((n: Natural) => BigDecimal(n.toBigInt))
  implicit val NaturalToRational = Conversion((n: Natural) => Rational(n.toBigInt))
  implicit val NaturalToAlgebraic = Conversion((n: Natural) => Algebraic(n.toBigInt))
  implicit val NaturalToReal = Conversion((n: Natural) => Real(n.toBigInt))
  
  // Byte conversions
  implicit val ByteToUByte = Coercion((n: Byte) => if (n >= 0) Some(UByte(n)) else None)
  implicit val ByteToUShort = Coercion((n: Byte) => if (n >= 0) Some(UShort(n)) else None)
  implicit val ByteToUInt = Coercion((n: Byte) => if (n >= 0) Some(UInt(n)) else None)
  implicit val ByteToULong = Coercion((n: Byte) => if (n >= 0) Some(ULong(n)) else None)
  implicit val ByteToNatural = Coercion((n: Byte) => if (n >= 0) Some(Natural(n)) else None)

  implicit val ByteToShort = Conversion((n: Byte) => n.toShort)
  implicit val ByteToInt = Conversion((n: Byte) => n.toInt)
  implicit val ByteToLong = Conversion((n: Byte) => n.toLong)
  implicit val ByteToBigInt = Conversion((n: Byte) => BigInt(n))
  implicit val ByteToSafeLong = Conversion((n: Byte) => SafeLong(n))
  implicit val ByteToFloat = Conversion((n: Byte) => n.toFloat)
  implicit val ByteToDouble = Conversion((n: Byte) => n.toDouble)
  implicit val ByteToBigDecimal = Conversion((n: Byte) => BigDecimal(n))
  implicit val ByteToRational = Conversion((n: Byte) => Rational(n))
  implicit val ByteToAlgebraic = Conversion((n: Byte) => Algebraic(n))
  implicit val ByteToReal = Conversion((n: Byte) => Real(n))
  
  // Short conversions
  implicit val ShortToUByte = Coercion((n: Short) => if (n >= 0 && n < Byte.MaxValue) Some(UByte(n)) else None)
  implicit val ShortToUShort = Coercion((n: Short) => if (n >= 0) Some(UShort(n)) else None)
  implicit val ShortToUInt = Coercion((n: Short) => if (n >= 0) Some(UInt(n)) else None)
  implicit val ShortToULong = Coercion((n: Short) => if (n >= 0) Some(ULong(n)) else None)
  implicit val ShortToNatural = Coercion((n: Short) => if (n >= 0) Some(Natural(n)) else None)
  implicit val ShortToByte = Coercion((n: Short) => if (n.isValidByte) Some(n.toByte) else None)

  implicit val ShortToInt = Conversion((n: Short) => n.toInt)
  implicit val ShortToLong = Conversion((n: Short) => n.toLong)
  implicit val ShortToBigInt = Conversion((n: Short) => BigInt(n))
  implicit val ShortToSafeLong = Conversion((n: Short) => SafeLong(n))
  implicit val ShortToFloat = Conversion((n: Short) => n.toFloat)
  implicit val ShortToDouble = Conversion((n: Short) => n.toDouble)
  implicit val ShortToBigDecimal = Conversion((n: Short) => BigDecimal(n))
  implicit val ShortToRational = Conversion((n: Short) => Rational(n))
  implicit val ShortToAlgebraic = Conversion((n: Short) => Algebraic(n))
  implicit val ShortToReal = Conversion((n: Short) => Real(n))
  
  // Int conversions
  implicit val IntToUByte = Coercion((n: Int) => if (n >= 0 && n < Byte.MaxValue) Some(UByte(n)) else None)
  implicit val IntToUShort = Coercion((n: Int) => if (n >= 0 && n < Short.MaxValue) Some(UShort(n)) else None)
  implicit val IntToUInt = Coercion((n: Int) => if (n >= 0) Some(UInt(n)) else None)
  implicit val IntToULong = Coercion((n: Int) => if (n >= 0) Some(ULong(n)) else None)
  implicit val IntToNatural = Coercion((n: Int) => if (n >= 0) Some(Natural(n)) else None)
  implicit val IntToByte = Coercion((n: Int) => if (n.isValidByte) Some(n.toByte) else None)
  implicit val IntToShort = Coercion((n: Int) => if (n.isValidShort) Some(n.toShort) else None)
  implicit val IntToFloat = Approx((n: Int) => Some(n.toFloat))

  implicit val IntToLong = Conversion((n: Int) => n.toLong)
  implicit val IntToBigInt = Conversion((n: Int) => BigInt(n))
  implicit val IntToSafeLong = Conversion((n: Int) => SafeLong(n))
  implicit val IntToDouble = Conversion((n: Int) => n.toDouble)
  implicit val IntToBigDecimal = Conversion((n: Int) => BigDecimal(n))
  implicit val IntToRational = Conversion((n: Int) => Rational(n))
  implicit val IntToAlgebraic = Conversion((n: Int) => Algebraic(n))
  implicit val IntToReal = Conversion((n: Int) => Real(n))
  
  // Long conversions
  implicit val LongToUByte = Coercion((n: Long) => if (n >= 0 && n < Byte.MaxValue) Some(UByte(n.toInt)) else None)
  implicit val LongToUShort = Coercion((n: Long) => if (n >= 0 && n < Short.MaxValue) Some(UShort(n.toInt)) else None)
  implicit val LongToUInt = Coercion((n: Long) => if (n >= 0 && n < Int.MaxValue) Some(UInt(n.toInt)) else None)
  implicit val LongToULong = Coercion((n: Long) => if (n >= 0) Some(ULong(n)) else None)
  implicit val LongToNatural = Coercion((n: Long) => if (n >= 0) Some(Natural(n)) else None)
  implicit val LongToByte = Coercion((n: Long) => if (n.isValidByte) Some(n.toByte) else None)
  implicit val LongToShort = Coercion((n: Long) => if (n.isValidShort) Some(n.toByte) else None)
  implicit val LongToInt = Coercion((n: Long) => if (n.isValidInt) Some(n.toInt) else None)
  implicit val LongToFloat = Approx((n: Long) => Some(n.toFloat))
  implicit val LongToDouble = Approx((n: Long) => Some(n.toDouble))

  implicit val LongToBigInt = Conversion((n: Long) => BigInt(n))
  implicit val LongToSafeLong = Conversion((n: Long) => SafeLong(n))
  implicit val LongToBigDecimal = Conversion((n: Long) => BigDecimal(n))
  implicit val LongToRational = Conversion((n: Long) => Rational(n))
  implicit val LongToAlgebraic = Conversion((n: Long) => Algebraic(n))
  implicit val LongToReal = Conversion((n: Long) => Real(n))
  
  // BigInt conversions
  implicit val BigIntToUByte = Coercion((n: BigInt) => if (n >= 0 && n < Byte.MaxValue) Some(UByte(n.toInt)) else None)
  implicit val BigIntToUShort = Coercion((n: BigInt) => if (n >= 0 && n < Short.MaxValue) Some(UShort(n.toInt)) else None)
  implicit val BigIntToUInt = Coercion((n: BigInt) => if (n >= 0 && n < Int.MaxValue) Some(UInt(n.toInt)) else None)
  implicit val BigIntToULong = Coercion((n: BigInt) => if (n >= 0 && n < Long.MaxValue) Some(ULong(n.toLong)) else None)
  implicit val BigIntToNatural = Coercion((n: BigInt) => if (n >= 0) Some(Natural(n)) else None)
  implicit val BigIntToByte = Coercion((n: BigInt) => if (n.isValidByte) Some(n.toByte) else None)
  implicit val BigIntToShort = Coercion((n: BigInt) => if (n.isValidShort) Some(n.toByte) else None)
  implicit val BigIntToInt = Coercion((n: BigInt) => if (n.isValidInt) Some(n.toInt) else None)
  implicit val BigIntToLong = Coercion((n: BigInt) => if (n.isValidLong) Some(n.toLong) else None)
  implicit val BigIntToFloat = Approx((n: BigInt) => Some(n.toFloat)) //fixme
  implicit val BigIntToDouble = Approx((n: BigInt) => Some(n.toDouble)) //fixme

  implicit val BigIntToSafeLong = Conversion((n: BigInt) => SafeLong(n))
  implicit val BigIntToBigDecimal = Conversion((n: BigInt) => BigDecimal(n))
  implicit val BigIntToRational = Conversion((n: BigInt) => Rational(n))
  implicit val BigIntToAlgebraic = Conversion((n: BigInt) => Algebraic(n))
  implicit val BigIntToReal = Conversion((n: BigInt) => Real(n))
  
  // SafeLong conversions
  implicit val SafeLongToUByte = Coercion((n: SafeLong) => if (n >= 0 && n < Byte.MaxValue) Some(UByte(n.toInt)) else None)
  implicit val SafeLongToUShort = Coercion((n: SafeLong) => if (n >= 0 && n < Short.MaxValue) Some(UShort(n.toInt)) else None)
  implicit val SafeLongToUInt = Coercion((n: SafeLong) => if (n >= 0 && n < Int.MaxValue) Some(UInt(n.toInt)) else None)
  implicit val SafeLongToULong = Coercion((n: SafeLong) => if (n >= 0 && n < Long.MaxValue) Some(ULong(n.toLong)) else None)
  implicit val SafeLongToNatural = Coercion((n: SafeLong) => if (n >= 0) Some(n.fold(Natural(_), Natural(_))) else None)
  implicit val SafeLongToByte = Coercion((n: SafeLong) => if (n.isValidByte) Some(n.toByte) else None)
  implicit val SafeLongToShort = Coercion((n: SafeLong) => if (n.isValidShort) Some(n.toByte) else None)
  implicit val SafeLongToInt = Coercion((n: SafeLong) => if (n.isValidInt) Some(n.toInt) else None)
  implicit val SafeLongToLong = Coercion((n: SafeLong) => if (n.isValidLong) Some(n.toLong) else None)
  implicit val SafeLongToFloat = Approx((n: SafeLong) => Some(n.toFloat)) //fixme
  implicit val SafeLongToDouble = Approx((n: SafeLong) => Some(n.toDouble)) //fixme

  implicit val SafeLongToBigInt = Conversion((n: SafeLong) => n.toBigInt)
  implicit val SafeLongToBigDecimal = Conversion((n: SafeLong) => BigDecimal(n.toBigInt))
  implicit val SafeLongToRational = Conversion((n: SafeLong) => Rational(n))
  implicit val SafeLongToAlgebraic = Conversion((n: SafeLong) => Algebraic(n))
  implicit val SafeLongToReal = Conversion((n: SafeLong) => Real(n))
  
  // Float conversions
  implicit val FloatToUByte = Coercion((n: Float) => if (n >= 0 && n.isValidByte) Some(UByte(n.toInt)) else None)
  implicit val FloatToUShort = Coercion((n: Float) => if (n >= 0 && n.isValidShort) Some(UShort(n.toInt)) else None)
  implicit val FloatToUInt = Coercion((n: Float) => if (n >= 0 && n.isValidInt) Some(UInt(n.toInt)) else None)
  implicit val FloatToULong = Coercion((n: Float) => if (n >= 0 && n <= Long.MaxValue && n.isWhole) Some(ULong(n.toLong)) else None)
  implicit val FloatToNatural = Coercion((n: Float) => if (n >= 0 && n.isWhole) Some(Natural(Rational(n).toBigInt)) else None)
  implicit val FloatToByte = Coercion((n: Float) => if (n.isValidByte) Some(n.toByte) else None)
  implicit val FloatToShort = Coercion((n: Float) => if (n.isValidShort) Some(n.toByte) else None)
  implicit val FloatToInt = Coercion((n: Float) => if (n.isValidInt) Some(n.toInt) else None)
  implicit val FloatToLong = Coercion((n: Float) => if (n >= Long.MinValue && n <= Long.MaxValue && n.isWhole) Some(n.toLong) else None)
  implicit val FloatToBigInt = Coercion((n: Float) => if (n.isWhole) Some(Rational(n).toBigInt) else None)
  implicit val FloatToSafeLong = Coercion((n: Float) => if (n.isWhole) Some(SafeLong(Rational(n).toBigInt)) else None)

  implicit val FloatToDouble = Conversion((n: Float) => n.toDouble)
  implicit val FloatToBigDecimal = Conversion((n: Float) => BigDecimal(n))
  implicit val FloatToRational = Conversion((n: Float) => Rational(n))
  implicit val FloatToAlgebraic = Conversion((n: Float) => Algebraic(n))
  implicit val FloatToReal = Conversion((n: Float) => Real(n))
  
  // Double conversions
  implicit val DoubleToUByte = Coercion((n: Double) => if (n >= 0 && n.isValidByte) Some(UByte(n.toInt)) else None)
  implicit val DoubleToUShort = Coercion((n: Double) => if (n >= 0 && n.isValidShort) Some(UShort(n.toInt)) else None)
  implicit val DoubleToUInt = Coercion((n: Double) => if (n >= 0 && n.isValidInt) Some(UInt(n.toInt)) else None)
  implicit val DoubleToULong = Coercion((n: Double) => if (n >= 0 && n <= Long.MaxValue && n.isWhole) Some(ULong(n.toLong)) else None)
  implicit val DoubleToNatural = Coercion((n: Double) => if (n >= 0 && n.isWhole) Some(Natural(Rational(n).toBigInt)) else None)
  implicit val DoubleToByte = Coercion((n: Double) => if (n.isValidByte) Some(n.toByte) else None)
  implicit val DoubleToShort = Coercion((n: Double) => if (n.isValidShort) Some(n.toByte) else None)
  implicit val DoubleToInt = Coercion((n: Double) => if (n.isValidInt) Some(n.toInt) else None)
  implicit val DoubleToLong = Coercion((n: Double) => if (n >= Long.MinValue && n <= Long.MaxValue && n.isWhole) Some(n.toLong) else None)
  implicit val DoubleToBigInt = Coercion((n: Double) => if (n.isWhole) Some(Rational(n).toBigInt) else None)
  implicit val DoubleToSafeLong = Coercion((n: Double) => if (n.isWhole) Some(SafeLong(Rational(n).toBigInt)) else None)
  implicit val DoubleToFloat = Approx((n: Double) => Some(n.toFloat))

  implicit val DoubleToBigDecimal = Conversion((n: Double) => BigDecimal(n))
  implicit val DoubleToRational = Conversion((n: Double) => Rational(n))
  implicit val DoubleToAlgebraic = Conversion((n: Double) => Algebraic(n))
  implicit val DoubleToReal = Conversion((n: Double) => Real(n))
  
  // BigDecimal conversions
  implicit val BigDecimalToUByte = Coercion((n: BigDecimal) => if (n >= 0 && n.isValidByte) Some(UByte(n.toInt)) else None)
  implicit val BigDecimalToUShort = Coercion((n: BigDecimal) => if (n >= 0 && n.isValidShort) Some(UShort(n.toInt)) else None)
  implicit val BigDecimalToUInt = Coercion((n: BigDecimal) => if (n >= 0 && n.isValidInt) Some(UInt(n.toInt)) else None)
  implicit val BigDecimalToULong = Coercion((n: BigDecimal) => if (n >= 0 && n <= Long.MaxValue && n.isWhole) Some(ULong(n.toLong)) else None)
  implicit val BigDecimalToNatural = Coercion((n: BigDecimal) => if (n >= 0 && n.isWhole) Some(Natural(Rational(n).toBigInt)) else None)
  implicit val BigDecimalToByte = Coercion((n: BigDecimal) => if (n.isValidByte) Some(n.toByte) else None)
  implicit val BigDecimalToShort = Coercion((n: BigDecimal) => if (n.isValidShort) Some(n.toByte) else None)
  implicit val BigDecimalToInt = Coercion((n: BigDecimal) => if (n.isValidInt) Some(n.toInt) else None)
  implicit val BigDecimalToLong = Coercion((n: BigDecimal) => if (n >= Long.MinValue && n <= Long.MaxValue && n.isWhole) Some(n.toLong) else None)
  implicit val BigDecimalToBigInt = Coercion((n: BigDecimal) => if (n.isWhole) Some(n.toBigInt) else None)
  implicit val BigDecimalToSafeLong = Coercion((n: BigDecimal) => if (n.isWhole) Some(SafeLong(n.toBigInt)) else None)
  implicit val BigDecimalToFloat = Approx((n: BigDecimal) => Some(n.toFloat))
  implicit val BigDecimalToDouble = Approx((n: BigDecimal) => Some(n.toDouble))

  implicit val BigDecimalToRational = Conversion((n: BigDecimal) => Rational(n))
  implicit val BigDecimalToAlgebraic = Conversion((n: BigDecimal) => Algebraic(n))
  implicit val BigDecimalToReal = Conversion((n: BigDecimal) => Real(n))
  
  // Rational conversions
  implicit val RationalToUByte = Coercion((n: Rational) => if (n >= 0 && n.isValidByte) Some(UByte(n.toInt)) else None)
  implicit val RationalToUShort = Coercion((n: Rational) => if (n >= 0 && n.isValidShort) Some(UShort(n.toInt)) else None)
  implicit val RationalToUInt = Coercion((n: Rational) => if (n >= 0 && n.isValidInt) Some(UInt(n.toInt)) else None)
  implicit val RationalToULong = Coercion((n: Rational) => if (n >= 0 && n <= Long.MaxValue && n.isWhole) Some(ULong(n.toLong)) else None)
  implicit val RationalToNatural = Coercion((n: Rational) => if (n >= 0 && n.isWhole) Some(Natural(Rational(n).toBigInt)) else None)
  implicit val RationalToByte = Coercion((n: Rational) => if (n.isValidByte) Some(n.toByte) else None)
  implicit val RationalToShort = Coercion((n: Rational) => if (n.isValidShort) Some(n.toByte) else None)
  implicit val RationalToInt = Coercion((n: Rational) => if (n.isValidInt) Some(n.toInt) else None)
  implicit val RationalToLong = Coercion((n: Rational) => if (n >= Long.MinValue && n <= Long.MaxValue && n.isWhole) Some(n.toLong) else None)
  implicit val RationalToBigInt = Coercion((n: Rational) => if (n.isWhole) Some(n.toBigInt) else None)
  implicit val RationalToSafeLong = Coercion((n: Rational) => if (n.isWhole) Some(SafeLong(n.toBigInt)) else None)
  implicit val RationalToFloat = Approx((n: Rational) => Some(n.toFloat))
  implicit val RationalToDouble = Approx((n: Rational) => Some(n.toDouble))

  implicit val RationalToAlgebraic = Conversion((n: Rational) => Algebraic(n))
  implicit val RationalToReal = Conversion((n: Rational) => Real(n))

  // TODO: Real and Algebraic?
}
