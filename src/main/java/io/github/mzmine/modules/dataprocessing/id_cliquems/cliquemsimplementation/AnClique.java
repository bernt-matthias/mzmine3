/*
 * Copyright 2006-2020 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 * USA
 */


package io.github.mzmine.modules.dataprocessing.id_cliquems.cliquemsimplementation;

import io.github.mzmine.datamodel.RawDataFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javafx.util.Pair;

public class AnClique {

  //TODO is RawDatafile required later in annotation?

  private List<PeakData> peakData;
  private RawDataFile dataFile;
  private NetworkCliqueMS network = new NetworkCliqueMS();
  boolean cliquesFound = false;
  boolean isoFound = false;
  public HashMap<Integer,List<Integer>> cliques = new HashMap<>();

  AnClique(List<PeakData> peakData, RawDataFile file){
    this.peakData = peakData;
    this.dataFile = file;
  }

  public List<PeakData> getPeakList(){
    return peakData;
  }

  public void changePeakDataList(List<PeakData> pd){
    this.peakData = pd;
  }

  public RawDataFile getRawDataFile(){
    return  dataFile;
  }

  public NetworkCliqueMS getNetwork(){
    return network;
  }

  public void computeCliqueFromResult(){
    List<Pair<Integer,Integer>> nodeCliqueList = this.network.getResultNode_clique();
    for(Pair<Integer,Integer> p : nodeCliqueList){
      if(this.cliques.containsKey(p.getValue())){
        this.cliques.get(p.getValue()).add(p.getKey());
      }
      else{
        List<Integer> l = new ArrayList<>();
        l.add(p.getKey());
        this.cliques.put(p.getValue(), l);
      }
    }
  }


}
