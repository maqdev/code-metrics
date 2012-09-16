package eu.inn.metrics.fingerprint

import collection.mutable.Stack
import org.scalatest._
import java.io.{BufferedReader, StringReader}

class TextFingerprintCalculatorTest extends FlatSpec {

  def readerFromString(s: String) = new BufferedReader(new StringReader(s))
  val calc = new TextFingerprintCalculator(3,3)

  def fingerprintFromString(s: String) = {
    calc.getFingerprint(readerFromString(s))
  }

  "A TextFingerprintCalculator" should "return empty TextFingerprints for empty text" in {
    val fp = fingerprintFromString("")
    assert(fp.fingerprint.isEmpty)
    assert(fp.nonWhitespaceMd5.isEmpty)
  }

  "A TextFingerprintCalculator" should "return non empty TextFingerprints for non empty text" in {
    val fp = fingerprintFromString("1")
    assert(fp.fingerprint.length === 1)
    assert(!fp.nonWhitespaceMd5.isEmpty)
  }

  "A TextFingerprintCalculator" should " ignore whitespaces" in {
    val fp = fingerprintFromString(" 1 \t 2\n5")
    val fp2 = fingerprintFromString("12\n5")
    assert(fp === fp2)
  }

  "A TextFingerprintCalculator" should " merge common lines into one c-gram" in {
    val fp = fingerprintFromString("1\n1\n1\n2\n2\n2\n3\n3\n3")
    assert(fp.fingerprint.length === 3)
    assert(fp.fingerprint(0).lineCount === 3)
    assert(fp.fingerprint(1).lineCount === 3)
    assert(fp.fingerprint(2).lineCount === 3)
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

  "A TextFingerprintCalculator" should " show show 66%-89% similarity" in {
    val similarity1 = calc.getSimilarity(
      fingerprintFromString("1\n2\n3\n4\n5\n6\n7\n8\n9"),
      fingerprintFromString("1\n2\n3\n4\n5\n6\n7\n8\n$")
    )
    assert(similarity1 >= 0.66 && similarity1 <= 0.89)

    val similarity2 = calc.getSimilarity(
      fingerprintFromString("1\n2\n3\n4\n5\n6\n7\n8\n9"),
      fingerprintFromString("1\n2\n3\n$\n5\n6\n7\n8\n9")
    )
    assert(similarity2 >= 0.66 && similarity2 <= 0.89)

    val similarity3 = calc.getSimilarity(
      fingerprintFromString("1\n2\n3\n4\n5\n6\n7\n8\n9"),
      fingerprintFromString("1\n$\n3\n4\n5\n6\n7\n8\n9")
    )
    assert(similarity3 >= 0.66 && similarity3 <= 0.89)

    val similarity4 = calc.getSimilarity(
      fingerprintFromString("1\n2\n3\n4\n5\n6\n7\n8\n9"),
      fingerprintFromString("1\n2\n3\n4\n5\n6\n7\n8")
    )
    assert(similarity4 >= 0.66 && similarity4 <= 0.89)

    val similarity5 = calc.getSimilarity(
      fingerprintFromString("1\n2\n3\n4\n5\n6\n7\n8\n9"),
      fingerprintFromString("2\n3\n4\n5\n6\n7\n8\n9")
    )
    assert(similarity5 >= 0.66 && similarity5 <= 0.89)
  }

  "A TextFingerprintCalculator" should " show show 42%-76% similarity" in {
    val similarity1 = calc.getSimilarity(
      fingerprintFromString("1\n2\n3\n4\n5\n6\n7\n8\n9"),
      fingerprintFromString("1\n2\n3\n4\n5\n6\n$\n$\n$")
    )
    assert(similarity1 >= 0.42 && similarity1 <= 0.76)

    val similarity2 = calc.getSimilarity(
      fingerprintFromString("1\n2\n3\n4\n5\n6\n7\n8\n9"),
      fingerprintFromString("1\n2\n3\n$\n$\n$\n7\n8\n9")
    )
    assert(similarity2 >= 0.42 && similarity2 <= 0.76)

    val similarity3 = calc.getSimilarity(
      fingerprintFromString("1\n2\n3\n4\n5\n6\n7\n8\n9"),
      fingerprintFromString("$\n$\n$\n4\n5\n6\n7\n8\n9")
    )
    assert(similarity3 >= 0.42 && similarity3 <= 0.76)
  }
}