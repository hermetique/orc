//
// SiteResolver.scala -- Scala traits SiteResolver, SiteResolution, and SiteForm
// Project OrcScala
//
// $Id$
//
// Created by jthywiss on May 30, 2010.
//
// Copyright (c) 2010 The University of Texas at Austin. All rights reserved.
//
// Use and redistribution of this file is governed by the license terms in
// the LICENSE file found in the project's top-level directory and also found at
// URL: http://orc.csres.utexas.edu/license.shtml .
//

package orc.sites

import orc.oil.nameless.Type // FIXME: Typechecker should operate on named types instead

/**
 * Trait of classes capable of resolving site names, such as {@link SiteForm}s.
 *
 * @author jthywiss
 */
abstract trait SiteResolver {
  def resolve(name: String): Site
}

/**
 * Supertype of forms of sites, such as Orc site, Java site, Web
 * service site, etc.
 *
 * @author jthywiss
 */
abstract trait SiteForm extends SiteResolver
//class OrcSiteForm extends SiteForm
//class JavaSiteForm extends SiteForm
//class WebServiceSiteForm extends SiteForm
