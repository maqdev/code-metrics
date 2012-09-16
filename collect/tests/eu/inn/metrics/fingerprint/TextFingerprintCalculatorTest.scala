package eu.inn.metrics.fingerprint

import collection.mutable.Stack
import org.scalatest._
import java.io.{BufferedReader, StringReader}

class TextFingerprintCalculatorTest extends FlatSpec {

  def readerFromString(s: String) = new BufferedReader(new StringReader(s))
  val calc = new TextFingerprintCalculator(10)

  def fingerprintFromString(s: String) = {
    calc.getFingerprint(readerFromString(s))
  }

  "A TextFingerprintCalculator" should "return empty TextFingerprints for empty text" in {
    val fp = fingerprintFromString("")
    assert(fp.fingerprintA.isEmpty)
    assert(fp.fingerprintB.isEmpty)
    assert(fp.nonWhitespaceMd5.isEmpty)
  }

  "A TextFingerprintCalculator" should "return non empty TextFingerprints for non empty text" in {
    val fp = fingerprintFromString("1")
    assert(!fp.fingerprintA.isEmpty)
    assert(!fp.fingerprintB.isEmpty)
    assert(!fp.nonWhitespaceMd5.isEmpty)
  }

  "A TextFingerprintCalculator" should " ignore whitespaces" in {
    val fp = fingerprintFromString(" 1 \t 2\n5")
    val fp2 = fingerprintFromString("12\n5")
    assert(fp === fp2)
  }

  "A TextFingerprintCalculator" should " show show totally different similarity for different texts" in {
    val a = fingerprintFromString("1")
    val b = fingerprintFromString("2")

    val similarity = calc.getSimilarity(a,b)
    assert(similarity <= 0.01)
  }

  "A TextFingerprintCalculator" should " show show 100% similarity for same texts ignoring whitespace and empty lines" in {
    val a = fingerprintFromString("1\n2\n3\n4\n5")
    val b = fingerprintFromString("1\t\n 2\n3\n\n4\n5 ")

    val similarity = calc.getSimilarity(a,b)
    assert(similarity >= 0.99)
  }

  "A getSimilarity" should " return ~90% similarity" in {
    val similarity1 = calc.getSimilarity(
      fingerprintFromString("0\n1\n2\n3\n4\n5\n6\n7\n8\n9"),
      fingerprintFromString("0\n1\n2\n3\n4\n5\n6\n7\n8\n$")
    )

    val similarity2 = calc.getSimilarity(
      fingerprintFromString("0\n1\n2\n3\n4\n5\n6\n7\n8\n9"),
      fingerprintFromString("0\n1\n2\n3\n$\n5\n6\n7\n8\n9")
    )

    val similarity3 = calc.getSimilarity(
      fingerprintFromString("0\n1\n2\n3\n4\n5\n6\n7\n8\n9"),
      fingerprintFromString("0\n1\n$\n3\n4\n5\n6\n7\n8\n9")
    )

    val similarity4 = calc.getSimilarity(
      fingerprintFromString("0\n1\n2\n3\n4\n5\n6\n7\n8\n9"),
      fingerprintFromString("0\n1\n2\n3\n4\n5\n6\n7\n8")
    )

    val similarity5 = calc.getSimilarity(
      fingerprintFromString("0\n1\n2\n3\n4\n5\n6\n7\n8\n9"),
      fingerprintFromString("0\n2\n3\n4\n5\n6\n7\n8\n9")
    )

    assert(similarity1 >= 0.85 && similarity1 <= 0.95)
    assert(similarity2 >= 0.85 && similarity2 <= 0.95)
    assert(similarity3 >= 0.85 && similarity3 <= 0.95)
    assert(similarity4 >= 0.85 && similarity4 <= 0.95)
    assert(similarity5 >= 0.85 && similarity5 <= 0.95)
  }

  "A getSimilarity" should " return ~50% similarity" in {
    val similarity1 = calc.getSimilarity(
      fingerprintFromString("0\n1\n2\n3\n4\n5\n6\n7\n8\n9"),
      fingerprintFromString("0\n$\n2\n$\n4\n$\n6\n$\n8\n$")
    )

    val similarity2 = calc.getSimilarity(
      fingerprintFromString("0\n1\n2\n3\n4\n5\n6\n7\n8\n9"),
      fingerprintFromString("0\n1\n2\n3")
    )

    val similarity3 = calc.getSimilarity(
      fingerprintFromString("0\n1\n2\n3\n4\n5\n6\n7\n8\n9"),
      fingerprintFromString("0\n1\n2\n3\n4\na\nb\nc\nd\ne")
    )

    assert(similarity1 >= 0.45 && similarity1 <= 0.55)
    assert(similarity2 >= 0.45 && similarity2 <= 0.60)
    assert(similarity3 >= 0.45 && similarity3 <= 0.55)
  }
}