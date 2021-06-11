package main.java;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/*
 * Get range of analyzable versions (+1 to compute list of files for the last one later on)
 * */
public class OutputManager {
	
	public Map<String, LocalDate> removeRecentReleases(Map<String, LocalDate> releaseDates) {
		
		Map<String,LocalDate> avRel = new HashMap<>();	
		//Order chronologically
		LinkedHashMap<String, LocalDate> chronoReleases = new LinkedHashMap<>();	 
		releaseDates.entrySet().stream().sorted(Map.Entry.comparingByValue())
					  .forEachOrdered(x -> chronoReleases.put(x.getKey(), x.getValue()));
				
		Iterator<String> keys = chronoReleases.keySet().iterator();
		int total = chronoReleases.size()/2;
		while(keys.hasNext() && total != 0) {
			String k = keys.next();
			avRel.put(k, chronoReleases.get(k));
			total--;
		}
		
		return avRel;
	}
}
