package main.java;

import java.time.LocalDate;
import java.time.Month;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class OutputFiltering {
	
	public Map<String,LocalDate> removeReleases(Map<String,LocalDate> releases) {
		Collection<LocalDate> list = releases.values();
		Iterator<LocalDate> iter = list.iterator();
		LocalDate limit = getLastAvailableDate(iter);
		
		Map<String,LocalDate> avRel = new HashMap<>();
		releases.forEach((k,v) -> {
			if(v.isBefore(limit)) {
				avRel.put(k, v);
			}
		});
		
		return avRel;
	}

	private LocalDate getLastAvailableDate(Iterator<LocalDate> iter) {
		LocalDate first = iter.next();
		LocalDate last = first;
		while(iter.hasNext()) {
			last = iter.next();
		}
		
		int firstYear = first.getYear();
		
		Month lastMonth = last.getMonth();
		int lastYear = last.getYear();
		
		int year = (firstYear + lastYear)/2;
		
		return LocalDate.of(year, lastMonth, 1);
	}
}
