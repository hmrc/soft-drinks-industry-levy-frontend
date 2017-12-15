package sdil.forms

import sdil.models.Litreage
import sdil.controllers.PackageCopackSmallVolumeController.form

class PackageCopackSmallVolumeFormSpec extends FormSpec {

  "The package copack small volume form" should {
    "require a number of litres at the low rate" in {
      val f = form(packageCopackData).bind(validData.updated(keys.lowRate, ""))
      mustContainError(f, keys.lowRate, "error.litreage.required")
    }

    "require a number of litres at the high rate" in {
      val f = form(packageCopackData).bind(validData.updated(keys.highRate, ""))
      mustContainError(f, keys.highRate, "error.litreage.required")
    }

    "require the low rate litres to be a number" in {
      val f = form(packageCopackData).bind(validData.updated(keys.lowRate, "loads"))
      mustContainError(f, keys.lowRate, "error.litreage.numeric")
    }

    "require the high rate litres to be a number" in {
      val f = form(packageCopackData).bind(validData.updated(keys.highRate, "loads"))
      mustContainError(f, keys.highRate, "error.litreage.numeric")
    }

    "require the low rate litres to be a whole number" in {
      val f = form(packageCopackData).bind(validData.updated(keys.lowRate, "1.1"))
      mustContainError(f, keys.lowRate, "error.litreage.numeric")
    }

    "require the high rate litres to be a whole number" in {
      val f = form(packageCopackData).bind(validData.updated(keys.highRate, "2.2"))
      mustContainError(f, keys.highRate, "error.litreage.numeric")
    }

    "require the low rate litres to be positive" in {
      val f = form(packageCopackData).bind(validData.updated(keys.lowRate, "-1"))
      mustContainError(f, keys.lowRate, "error.litreage.min")
    }

    "require the high rate litres to be positive" in {
      val f = form(packageCopackData).bind(validData.updated(keys.highRate, "-2"))
      mustContainError(f, keys.highRate, "error.litreage.min")
    }

    "require the low rate litres to be less than the total copacked low rate litres" in {
      val packageCopackData = Litreage(atLowRate = 1, atHighRate = 1)

      val f = form(packageCopackData).bind(validData.updated(keys.lowRate, "2"))
      mustContainError(f, keys.lowRate, "error.copack-small.lower-greater-than-total-lower")
    }

    "require the high rate litres to be less than the total copacked high rate litres" in {
      val packageCopackData = Litreage(atLowRate = 1, atHighRate = 1)

      val f = form(packageCopackData).bind(validData.updated(keys.highRate, "2"))
      mustContainError(f, keys.highRate, "error.copack-small.higher-greater-than-total-higher")
    }

    "require the low rate litres to be less than 10,000,000,000,000" in {
      val f = form(packageCopackData).bind(validData.updated(keys.lowRate, "10000000000000"))
      mustContainError(f, keys.lowRate, "error.litreage.max")
    }

    "require the high rate litres to be less than 10,000,000,000,000" in {
      val f = form(packageCopackData).bind(validData.updated(keys.highRate, "10000000000000"))
      mustContainError(f, keys.highRate, "error.litreage.max")
    }

    "bind to Litreage when the form data is valid" in {
      val f = form(packageCopackData).bind(validData)
      f.value mustBe Some(Litreage(1, 2))
    }
  }

  lazy val keys = new {
    val lowRate = "lowerRateLitres"
    val highRate = "higherRateLitres"
  }

  lazy val validData = Map(
    keys.lowRate -> "1",
    keys.highRate -> "2"
  )

  lazy val packageCopackData = Litreage(atLowRate = 999999, atHighRate = 99999)
}
