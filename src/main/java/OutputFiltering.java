package main.java;

import java.time.LocalDate;
import java.time.Month;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/*
 * Get range of analyzable versions (+1 to compute list of files for the last one later on)
 * */
public class OutputFiltering {
	
	public Map<String,LocalDate> removeReleases(Map<String,LocalDate> releases) {
		LocalDate actual = getLastAvailableDate(releases);
		
		//Create new map with given releases
		Map<String,LocalDate> avRel = new HashMap<>();		
		releases.forEach((k,v) -> {
			if(v.isBefore(actual)) {
				avRel.put(k, v);
			}
		});
		
		return avRel;
	}

	private LocalDate getLastAvailableDate(Map<String,LocalDate> releases) {
		//Get limit value
		Collection<LocalDate> list = releases.values();
		Iterator<LocalDate> iter = list.iterator();
		LocalDate limit = getLimitDate(iter);
		
		//Get last revision to retrieve
		iter = list.iterator();
		LocalDate actual = null;
		LocalDate curr;
		while(iter.hasNext()) {
			curr = iter.next();
			if(curr.isAfter(limit) && (actual == null || curr.isBefore(actual))) {
				actual = curr;
			}
		}
		
		return actual;
	}

	private LocalDate getLimitDate(Iterator<LocalDate> iter) {
		LocalDate first = iter.next();
		int firstYear = first.getYear();
		
		LocalDate last = first;
		
		while(iter.hasNext()) {
			last = iter.next();
		}
				
		Month lastMonth = last.getMonth();
		int lastYear = last.getYear();
		
		int year = (firstYear + lastYear)/2;
		
		return LocalDate.of(year, lastMonth, 1);
	}
}
