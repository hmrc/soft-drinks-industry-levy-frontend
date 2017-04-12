package uk.gov.hmrc.softdrinksindustrylevyfrontend.util

import scala.util.Random

object RandomUtils {

  def randString(howManyChars: Integer): String = {
    Random.alphanumeric take howManyChars mkString ""
  }

  def randNumbers(howManyNos: Integer): String = {
    Seq.fill(howManyNos)(Random.nextInt(9)).mkString("")
  }

  def orderedRandNumbers(howManyNos: Integer): String = Seq.fill(howManyNos)(Random.nextInt(9)).sorted.mkString("")
}
