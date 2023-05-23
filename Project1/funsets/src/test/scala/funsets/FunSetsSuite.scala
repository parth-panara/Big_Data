package funsets

import org.scalatest.funsuite.AnyFunSuite

class FunSetsSuite extends AnyFunSuite {
  
  import FunSets._
   



  /**
   * Link to the scaladoc - very clear and detailed tutorial of AnyFunSuite:
   *
   * https://www.scalatest.org/scaladoc/3.2.15/org/scalatest/funsuite/AnyFunSuite.html
   *
   * Operators
   *  - test
   *  - ignore
   *  - pending
   */

  /**
   * Tests are written using the "test" operator and the "assert" method.
   */
  test("string take") {
    val message = "hello, world"
    assert(message.take(5) == "hello")
  }

  /**
   * For ScalaTest tests, there exists a special equality operator "===" that
   * can be used inside "assert". If the assertion fails, the two values will
   * be printed in the error message. Otherwise, when using "==", the test
   * error message will only say "assertion failed", without showing the values.
   *
   * Try it out! Change the values so that the assertion fails, and look at the
   * error message.
   */
  test("adding ints") {
    assert(1 + 2 === 3)
  }
  
  test("contains is implemented") {
    assert(contains(x => true, 100))
  }
  
  /**
   * When writing tests, one would often like to re-use certain values for multiple
   * tests. For instance, we would like to create an Int-set and have multiple test
   * about it.
   * 
   * Instead of copy-pasting the code for creating the set into every test, we can
   * store it in the test class using a val:
   * 
   *   val s1 = singletonSet(1)
   * 
   * However, what happens if the method "singletonSet" has a bug and crashes? Then
   * the test methods are not even executed, because creating an instance of the
   * test class fails!
   * 
   * Therefore, we put the shared values into a separate trait (traits are like
   * abstract classes), and create an instance inside each test method.
   * 
   */

  trait TestSets {
    val s1 = singletonSet(1)
    val s2 = singletonSet(2)
    val s3 = singletonSet(3)
    val s4 = union(s1, s2)
    val s5 = union(s2, s3)
  }

  /**
   * This test is currently disabled (by using "ignore") because the method
   * "singletonSet" is not yet implemented and the test would fail.
   * 
   * Once you finish your implementation of "singletonSet", exchange the
   * function "ignore" by "test".
   */
  test("singletonSet(1) contains 1") {
    
    /**
     * We create a new instance of the "TestSets" trait, this gives us access
     * to the values "s1" to "s3". 
     */
    new TestSets {
      /**
       * The string argument of "assert" is a message that is printed in case
       * the test fails. This helps identifying which assertion failed.
       */
      assert(contains(s1, 1), "Singleton")
    }
  }

  test("union contains all elements") {
    new TestSets {
      val s = union(s1, s2)
      assert(contains(s, 1), "Union 1")
      assert(contains(s, 2), "Union 2")
      assert(!contains(s, 3), "Union 3")
      assert(contains(union(s, s3), 3), "Union 4")
    }
  }
  
  test("intersect contains common elements") {
    new TestSets {
      val s = intersect(union(s1, s2), union(s2, s3))
      assert(contains(s, 2), "Intersect 1")
      assert(!contains(s, 1), "Intersect 2")
      assert(!contains(s, 3), "Intersect 3")
    }
  }

  
  test("diff contains all elements of A that are not in B") {
    new TestSets {
      val s = union(union(s1, s2), s3)
      val t = union(s2, s3)
      val diffSet = diff(s, t)
      assert(contains(diffSet, 1), "Diff 1")
      assert(!contains(diffSet, 2), "Diff 2")
      assert(!contains(diffSet, 3), "Diff 3")
    }
  }
  
  test("filter returns subset of elements satisfying predicate") {
    new TestSets {
      val s = union(union(s1, s2), s3)
      val filtered = filter(s, x => x % 2 == 0)
      assert(contains(filtered, 2), "Filter 1")
      assert(!contains(filtered, 1), "Filter 2")
      assert(!contains(filtered, 3), "Filter 3")
    }
  }
  
  test("forall checks if all elements match a predicate") {
    new TestSets {
      assert(forall(s4, x => x > 0), "Forall 1")
      assert(!forall(s4, x => x == 2), "Forall 2")
    }
  }
  
  test("exists checks if any element matches a predicate") {
    new TestSets {
      assert(exists(s4, x => x > 0), "Exists 1")
      assert(exists(s4, x => x == 2), "Exists 2")
      assert(!exists(s4, x => x == 3), "Exists 3")
    }
  }
  
  test("map applies function to each element") {
    new TestSets {
      val s = union(s1, s2)
      val mapped = map(s, x => x * x)
      assert(contains(mapped, 1), "Map 1")
      assert(contains(mapped, 4), "Map 2")
      assert(!contains(mapped, 2), "Map 3")
    }
  }
  test("union") {
    new TestSets {
      val s = union(s1, s2)
      assert(contains(s, 1), "Union 1")
      assert(contains(s, 2), "Union 2")
      assert(!contains(s, 3), "Union 3")
    }
  }

  test("intersect") {
    new TestSets {
      val s = intersect(s1, s2)
      assert(!contains(s, 1), "Intersect 1")
      assert(!contains(s, 2), "Intersect 2")
    }
  }

  test("diff ") {
    new TestSets {
      val s = diff(s1, s2)
      assert(contains(s, 1), "Diff 1")
      assert(!contains(s, 2), "Diff 2")
    }
  }

  test("filter") {
    new TestSets {
      val s = union(union(s1, s2), s3)
      val f = filter(s, x => x % 2 == 0)
      assert(!contains(f, 1), "Filter 1")
      assert(contains(f, 2), "Filter 2")
      assert(!contains(f, 3), "Filter 3")
    }
  }

  test("map") {
    new TestSets {
      val s = union(union(s1, s2), s3)
      val m = map(s, x => x * 2)
      assert(!contains(m, 1), "Map 1")
      assert(contains(m, 2), "Map 2")
      assert(!contains(m, 3), "Map 3")
      assert(contains(m, 4), "Map 4")
      assert(contains(m, 6), "Map 6")
    }
  }







}