/*
 * Copyright 2006-2021 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package io.github.mzmine.modules.io.export_library_analysis_csv;

import io.github.mzmine.datamodel.DataPoint;
import io.github.mzmine.modules.visualization.spectra.simplespectra.datapointprocessing.isotopes.MassListDeisotoper;
import io.github.mzmine.modules.visualization.spectra.simplespectra.datapointprocessing.isotopes.MassListDeisotoperParameters;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.DataPointSorter;
import io.github.mzmine.util.files.FileAndPathUtil;
import io.github.mzmine.util.io.CSVUtils;
import io.github.mzmine.util.maths.similarity.Similarity;
import io.github.mzmine.util.scans.ScanAlignment;
import io.github.mzmine.util.scans.ScanUtils;
import io.github.mzmine.util.scans.similarity.Weights;
import io.github.mzmine.util.spectraldb.entry.DBEntryField;
import io.github.mzmine.util.spectraldb.entry.SpectralDBEntry;
import io.github.mzmine.util.spectraldb.entry.SpectralLibrary;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class LibraryAnalysisCSVExportTask extends AbstractTask {

  private static final String[] LIB = {"IDa", "IDb", "name_a", "name_b", "adduct_a", "adduct_b",
      "instrument_a", "instrument_b", "signals_a", "signals_b", "SMILES_a", "SMILES_b", "inchi_a",
      "inchi_b", "inchi_key_a", "inchi_key_b", "inchi_key_equals"};
  private static final String[] SIM_TYPES = {"cos", "modcos", "nl"};
  private static final String[] VALUES = {"matched_n", "matched_rel", "matched_intensity",
      "matched_intensity_a", "matched_intensity_b", "score", "max_contribution",
      "signal_contributions", "signals_contr_gr_0_05", "signals_contr_gr_0_2"};
  private static final String EMPTY_VALUES = ",,,,,,,,";

  private static final DecimalFormat format = new DecimalFormat("0.000");
  private static final Logger logger = Logger.getLogger(
      LibraryAnalysisCSVExportTask.class.getName());
  private final String fieldSeparator;
  private final MZTolerance mzTol;
  private final Integer minMatchedSignals;
  private final Boolean deisotope;
  private final MassListDeisotoperParameters deisotoperParameters;
  // parameter values
  private File fileName;
  private final AtomicLong processedTypes = new AtomicLong(0);
  private long totalTypes = 0;
  private final List<SpectralLibrary> libraries;
  private final Weights weights;
  private final boolean applyRemovePrecursorRange;
  private final MZTolerance removePrecursorRange;

  public LibraryAnalysisCSVExportTask(ParameterSet parameters, @NotNull Instant moduleCallDate) {
    super(null, moduleCallDate); // no new data stored -> null
    libraries = parameters.getParameter(LibraryAnalysisCSVExportParameters.libraries).getValue()
        .getMatchingLibraries();
    fileName = parameters.getParameter(LibraryAnalysisCSVExportParameters.filename).getValue();
    fieldSeparator = parameters.getParameter(LibraryAnalysisCSVExportParameters.fieldSeparator)
        .getValue();
    weights = parameters.getValue(LibraryAnalysisCSVExportParameters.weight);
    mzTol = parameters.getValue(LibraryAnalysisCSVExportParameters.mzTolerance);
    minMatchedSignals = parameters.getValue(LibraryAnalysisCSVExportParameters.minMatch);
    applyRemovePrecursorRange = parameters.getParameter(
        LibraryAnalysisCSVExportParameters.removePrecursorRange).getValue();
    removePrecursorRange = parameters.getParameter(
        LibraryAnalysisCSVExportParameters.removePrecursorRange).getEmbeddedParameter().getValue();

    deisotope = parameters.getParameter(LibraryAnalysisCSVExportParameters.deisotoping).getValue();
    deisotoperParameters =
        deisotope ? parameters.getParameter(LibraryAnalysisCSVExportParameters.deisotoping)
            .getEmbeddedParameters() : null;
  }


  @Override
  public double getFinishedPercentage() {
    if (totalTypes == 0) {
      return 0;
    }
    return (double) processedTypes.get() / (double) totalTypes;
  }

  @Override
  public String getTaskDescription() {
    return String.format("Exporting library correlations to CSV file: %d / %d",
        processedTypes.get(), totalTypes);
  }

  /**
   * @param precursorMZ
   * @param dp
   * @return true if data point is accepted and matches all requirements
   */
  public boolean filter(double precursorMZ, DataPoint dp) {
    // remove data point if
    return !applyRemovePrecursorRange || !removePrecursorRange.checkWithinTolerance(precursorMZ,
        dp.getMZ());
  }


  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);

    // Total number of rows
    for (var lib : libraries) {
      totalTypes += lib.size();
    }
    long noMz = 0;
    long lessSignals = 0;
    long containsZeroIntensity = 0;

    // prepare all spectra by filtering and weighting
    List<FilteredSpec> spectra = new ArrayList<>();
    // prepare the spectra
    for (var lib : libraries) {
      for (SpectralDBEntry entry : lib.getEntries()) {
        final Double mz = entry.getPrecursorMZ();
        if (mz == null) {
          noMz++;
          continue;
        }
        // filter data points
        boolean containsZero = false;
        List<DataPoint> filtered = new ArrayList<>();
        for (DataPoint dp : entry.getDataPoints()) {
          if (Double.compare(dp.getIntensity(), 0) <= 0) {
            containsZero = true;
            break;
          }
          if (!filter(mz, dp)) {
            continue;
          }
          // apply weights
          filtered.add(weights.getWeighted(dp));
        }
        // filtering finished
        if (containsZero) {
          containsZeroIntensity++;
        } else {
          DataPoint[] filteredArray = filtered.toArray(DataPoint[]::new);
          // remove isotopes
          if (deisotope) {
            filteredArray = MassListDeisotoper.filterIsotopes(filteredArray, deisotoperParameters);
          }
          if (filteredArray.length < minMatchedSignals) {
            lessSignals++;
          } else {
            // sort by intensity for spectral matching later and add
            final DataPoint[] neutralLosses = ScanUtils.getNeutralLossSpectrum(filteredArray, mz);
            Arrays.sort(filteredArray, DataPointSorter.DEFAULT_INTENSITY);
            Arrays.sort(neutralLosses, DataPointSorter.DEFAULT_INTENSITY);
            spectra.add(new FilteredSpec(entry, filteredArray, neutralLosses, mz));
          }
        }
      }
    }

    final int numSpec = spectra.size();
    logger.info(String.format(
        "Prepared all library spectra %d. Filtered %d without precursor m/z; %d below %d signals; %d with zero intensity values",
        numSpec, noMz, lessSignals, minMatchedSignals, containsZeroIntensity));

    List<FilteredSpec[]> pairs = new ArrayList<>();
    for (int i = 0; i < spectra.size() - 1; i++) {
      for (int k = i + 1; k < spectra.size(); k++) {
        pairs.add(new FilteredSpec[]{spectra.get(i), spectra.get(k)});
      }
    }
    totalTypes = pairs.size();

    ConcurrentLinkedDeque<String> outputList = new ConcurrentLinkedDeque<>();
    // process all in parallel and map to string lines
    pairs.stream().parallel().forEach(pair -> {
      if (!isCanceled()) {
        String line = matchToCsvString(pair[0], pair[1]);
        if (line != null) {
          outputList.add(line);
        }
        processedTypes.incrementAndGet();
      }
    });

    fileName = FileAndPathUtil.getRealFilePath(fileName, "csv");

    // Open file
    try (BufferedWriter writer = Files.newBufferedWriter(fileName.toPath(),
        StandardCharsets.UTF_8)) {

      // header
      for (String s : LIB) {
        writer.append(s).append(fieldSeparator);
      }
      writer.append(
          Arrays.stream(SIM_TYPES).flatMap(sim -> Arrays.stream(VALUES).map(v -> sim + "_" + v))
              .collect(Collectors.joining(fieldSeparator)));
      // add comparison

      writer.append("\n");

      // data
      for (String s : outputList) {
        writer.append(s).append("\n");
      }
//      writer.append(output);

    } catch (IOException e) {
      setStatus(TaskStatus.ERROR);
      setErrorMessage("Could not open file " + fileName + " for writing.");
      logger.log(Level.WARNING, String.format(
          "Error writing spectral similarity CSV format to file: %s for libraries: %s. Message: %s",
          fileName.getAbsolutePath(),
          libraries.stream().map(SpectralLibrary::getName).collect(Collectors.joining(",")),
          e.getMessage()), e);
      return;
    }

    if (getStatus() == TaskStatus.PROCESSING) {
      setStatus(TaskStatus.FINISHED);
    }
  }

  private String matchToCsvString(FilteredSpec a, FilteredSpec b) {
//    private static final String[] LIB = {"IDa", "IDb", "signals_a", "signals_b", "SMILES_a",
//        "SMILES_b", "inchi_a", "inchi_b", "inchi_key_a", "inchi_key_b", "inchi_key_equals"};
    // String[] SIM = {"cos", "modcos", "nl"};
    String cos = getSimilarity(a.dps(), b.dps(), a.precursorMZ(), b.precursorMZ(), false);
    String modcos = getSimilarity(a.dps(), b.dps(), a.precursorMZ(), b.precursorMZ(), true);
    String nl = getSimilarity(a.neutralLosses(), b.neutralLosses(), a.precursorMZ(),
        b.precursorMZ(), false);

    if (cos == null && modcos == null && nl == null) {
      processedTypes.incrementAndGet();
      return null;
    }

    if (cos == null) {
      cos = EMPTY_VALUES;
    }
    if (modcos == null) {
      modcos = EMPTY_VALUES;
    }
    if (nl == null) {
      nl = EMPTY_VALUES;
    }

    StringBuilder line = new StringBuilder();
    // add library specifics
    final SpectralDBEntry ea = a.entry();
    final SpectralDBEntry eb = b.entry();
    final String inchiA = ea.getOrElse(DBEntryField.INCHIKEY, "").trim();
    final String inchiB = eb.getOrElse(DBEntryField.INCHIKEY, "").trim();

    append(line, ea.getOrElse(DBEntryField.ENTRY_ID, "NO_ID"));
    append(line, eb.getOrElse(DBEntryField.ENTRY_ID, "NO_ID"));
    append(line, ea.getOrElse(DBEntryField.NAME, ""));
    append(line, eb.getOrElse(DBEntryField.NAME, ""));
    append(line, ea.getOrElse(DBEntryField.ION_TYPE, ""));
    append(line, eb.getOrElse(DBEntryField.ION_TYPE, ""));
    append(line, ea.getOrElse(DBEntryField.INSTRUMENT_TYPE, ""));
    append(line, eb.getOrElse(DBEntryField.INSTRUMENT_TYPE, ""));
    line.append(a.dps().length).append(fieldSeparator);
    line.append(b.dps().length).append(fieldSeparator);
    append(line, ea.getOrElse(DBEntryField.SMILES, ""));
    append(line, eb.getOrElse(DBEntryField.SMILES, ""));
    append(line, ea.getOrElse(DBEntryField.INCHI, ""));
    append(line, eb.getOrElse(DBEntryField.INCHI, ""));
    append(line, inchiA);
    append(line, inchiB);
    line.append(inchiA.length() > 0 && inchiA.equals(inchiB)).append(fieldSeparator);

    // add similarities
    line.append(cos).append(fieldSeparator);
    line.append(modcos).append(fieldSeparator);
    line.append(nl);

    processedTypes.incrementAndGet();
    return line.toString();
  }

  private void append(StringBuilder line, String val) {
    line.append(csvEscape(val)).append(fieldSeparator);
  }

  public String getSimilarity(DataPoint[] sortedA, DataPoint[] sortedB, double precursorMzA,
      double precursorMzB, boolean modAware) {

    // align
    final List<DataPoint[]> aligned = alignDataPoints(sortedA, sortedB, precursorMzA, precursorMzB,
        modAware);
    int matched = calcOverlap(aligned);

    if (matched < minMatchedSignals) {
      return null;
    }

    double matchedRel = matched / (double) aligned.size();

    // cosine
    double[][] diffArray = ScanAlignment.toIntensityArray(aligned);

    final double cosineDivisor = Similarity.cosineDivisor(diffArray);

    int signalsGr0_05 = 0;
    int signalsGr0_2 = 0;
    double maxContribution = 0;
    double totalIntensityA = 0;
    double totalIntensityB = 0;
    double explainedIntensityA = 0;
    double explainedIntensityB = 0;
    double cosine = 0;
    double[] contributions = new double[diffArray.length];
    for (int i = 0; i < diffArray.length; i++) {
      final double[] pair = diffArray[i];
      contributions[i] = Similarity.cosineSignalContribution(pair, cosineDivisor);
      cosine += contributions[i];
      totalIntensityA += pair[0];
      totalIntensityB += pair[1];

      if (pair[0] > 0 && pair[1] > 0) {
        // matched
        explainedIntensityA += pair[0];
        explainedIntensityB += pair[1];
      }
      if (contributions[i] >= 0.05) {
        signalsGr0_05++;
      }
      if (contributions[i] >= 0.2) {
        signalsGr0_2++;
      }

      if (contributions[i] > maxContribution) {
        maxContribution = maxContribution;
      }
    }
    explainedIntensityA /= totalIntensityA;
    explainedIntensityB /= totalIntensityB;
    double explainedIntensity = (explainedIntensityA + explainedIntensityB) / 2d;

    // sort by contribution
    final String contributionString = Arrays.stream(contributions).filter(d -> d >= 0.001).boxed()
        .sorted((a, b) -> Double.compare(b, a)).map(format::format)
        .collect(Collectors.joining(";"));

    StringBuilder line = new StringBuilder();
    line.append(matched).append(fieldSeparator);
    line.append(matchedRel).append(fieldSeparator);
    line.append(format.format(explainedIntensity)).append(fieldSeparator);
    line.append(format.format(explainedIntensityA)).append(fieldSeparator);
    line.append(format.format(explainedIntensityB)).append(fieldSeparator);
    line.append(format.format(cosine)).append(fieldSeparator);
    line.append(format.format(maxContribution)).append(fieldSeparator);
    line.append(contributionString).append(fieldSeparator);
    line.append(format.format(signalsGr0_05)).append(fieldSeparator);
    line.append(format.format(signalsGr0_2));
    return line.toString();
  }

  /**
   * Calculate overlap
   *
   * @param aligned
   * @return
   */
  protected int calcOverlap(List<DataPoint[]> aligned) {
    int n = 0;
    for (var pair : aligned) {
      if (pair[0] != null && pair[1] != null) {
        n++;
      }
    }
    return n;
  }

  @NotNull
  private List<DataPoint[]> alignDataPoints(DataPoint[] sortedA, DataPoint[] sortedB,
      double precursorMzA, double precursorMzB, boolean modAware) {
    if (modAware) {
      return ScanAlignment.alignOfSortedModAware(mzTol, sortedA, sortedB, precursorMzA,
          precursorMzB);
    } else {
      return ScanAlignment.alignOfSorted(mzTol, sortedA, sortedB);
    }
  }

  @SuppressWarnings("rawtypes")
  private String csvEscape(String input) {
    return CSVUtils.escape(input, fieldSeparator);
  }

}
