package jp.ponko2.android.webime.tests

import junit.framework.Assert._
import _root_.android.test.AndroidTestCase

class UnitTests extends AndroidTestCase {
  def testPackageIsCorrect {
    assertEquals("jp.ponko2.android.webime", getContext.getPackageName)
  }
}