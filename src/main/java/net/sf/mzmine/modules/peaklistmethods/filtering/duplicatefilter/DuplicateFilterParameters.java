/*
 * Copyright 2006-2015 The MZmine 2 Development Team
 * 
 * This file is part of MZmine 2.
 * 
 * MZmine 2 is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * MZmine 2 is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * MZmine 2; if not, write to the Free Software Foundation, Inc., 51 Franklin St,
 * Fifth Floor, Boston, MA 02110-1301 USA
 */

package net.sf.mzmine.modules.peaklistmethods.filtering.duplicatefilter;

import net.sf.mzmine.parameters.Parameter;
import net.sf.mzmine.parameters.impl.SimpleParameterSet;
import net.sf.mzmine.parameters.parametertypes.BooleanParameter;
import net.sf.mzmine.parameters.parametertypes.StringParameter;
import net.sf.mzmine.parameters.parametertypes.selectors.PeakListsParameter;
import net.sf.mzmine.parameters.parametertypes.tolerances.MZToleranceParameter;
import net.sf.mzmine.parameters.parametertypes.tolerances.RTToleranceParameter;

public class DuplicateFilterParameters extends SimpleParameterSet {

    public static final PeakListsParameter peakLists = new PeakListsParameter();

    public static final StringParameter suffix = new StringParameter(
	    "Name suffix", "Suffix to be added to peak list name", "filtered");

    public static final MZToleranceParameter mzDifferenceMax = new MZToleranceParameter(
	    "m/z tolerance", "Maximum m/z difference between duplicate peaks");
    public static final RTToleranceParameter rtDifferenceMax = new RTToleranceParameter(
	    "RT tolerance",
	    "Maximum retention time difference between duplicate peaks");

    public static final BooleanParameter requireSameIdentification = new BooleanParameter(
	    "Require same identification",
	    "If checked, duplicate peaks must have same identification(s)");

    public static final BooleanParameter autoRemove = new BooleanParameter(
	    "Remove original peaklist",
	    "If checked, original peaklist will be removed and only deisotoped version remains");

    public DuplicateFilterParameters() {
	super(new Parameter[] { peakLists, suffix, mzDifferenceMax,
		rtDifferenceMax, requireSameIdentification, autoRemove, });
    }

}
