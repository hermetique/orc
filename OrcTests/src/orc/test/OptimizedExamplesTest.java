//
// TypedExamplesTest.java -- Java class TypedExamplesTest
// Project OrcTests
//
// $Id: TypedExamplesTest.java 3261 2013-09-08 14:02:44Z jthywissen $
//
// Created by dkitchin on Mar 30, 2011.
//
// Copyright (c) 2013 The University of Texas at Austin. All rights reserved.
//
// Use and redistribution of this file is governed by the license terms in
// the LICENSE file found in the project's top-level directory and also found at
// URL: http://orc.csres.utexas.edu/license.shtml .
//

package orc.test;

import java.io.File;

import junit.framework.Test;
import orc.script.OrcBindings;
import orc.test.TestUtils.OrcTestCase;

/**
 * Test Orc by running annotated sample programs from the "../OrcExamples" directory.
 * The Orc type checker is enabled for these tests.
 * Each program is given at most 10 seconds to complete.
 * <p>
 * We look for one or more comment blocks formatted per
 * <code>ExampleOutput</code>'s specs.
 *
 * @see ExpectedOutput
 * @author dkitchin
 */
public class OptimizedExamplesTest {

  public static Test suite() {
    OrcBindings bindings = new OrcBindings();

    // Turn on typechecking
    bindings.optimizationLevel_$eq(3);

    return TestUtils.buildSuite(OptimizedExamplesTest.class.getSimpleName(), OptimizedExamplesTestCase.class, bindings, new File("test_data"), new File("../OrcExamples"));
  }

  public static class OptimizedExamplesTestCase extends OrcTestCase {
      /* No overrides */
  }

}
