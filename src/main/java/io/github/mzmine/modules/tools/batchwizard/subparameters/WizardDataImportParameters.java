/*
 * Copyright (c) 2004-2022 The MZmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.modules.tools.batchwizard.subparameters;

import io.github.mzmine.modules.io.import_rawdata_all.AllSpectralDataImportParameters;
import io.github.mzmine.modules.tools.batchwizard.WizardPart;
import io.github.mzmine.modules.tools.batchwizard.WizardPreset;
import io.github.mzmine.modules.tools.batchwizard.subparameters.WizardMassSpectrometerParameters.MsInstrumentDefaults;

/**
 * Reuses the filenames {@link AllSpectralDataImportParameters}
 *
 * @author Robin Schmid <a href="https://github.com/robinschmid">https://github.com/robinschmid</a>
 */
public final class WizardDataImportParameters extends AbstractWizardParameters<String> {

  /**
   * There is only one preset no other options. If there are multiple options use an enum, see
   * {@link WizardMassSpectrometerParameters} and {@link MsInstrumentDefaults}
   */
  public static final String ONLY_PRESET = "Data";

  public WizardDataImportParameters() {
    super(WizardPart.DATA_IMPORT, ONLY_PRESET,
        // parameters
        AllSpectralDataImportParameters.fileNames);
  }

  public static WizardPreset createPreset() {
    return new WizardPreset(ONLY_PRESET, ONLY_PRESET, new WizardDataImportParameters());
  }

  @Override
  public String[] getPresetChoices() {
    return new String[]{ONLY_PRESET};
  }
}
