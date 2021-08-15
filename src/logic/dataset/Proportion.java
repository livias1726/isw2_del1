package logic.dataset;

import java.util.List;

/*
 * Implementation of the Proportion algorithm 
 * to compute the Injected Version of bugs
 * 
 * P = (FV-IV)/(FV-OV)
 */
public class Proportion {
	
	private double p;
	
	private static Proportion instance = null;

    private Proportion() {
    	/**/
    }

    public static Proportion getInstance() {
        if(instance == null) {
        	instance = new Proportion();
        }

        return instance;
    }
	
	public Integer getInjectedVersion(int opening, int fixed) {
		return (int) Math.floor(fixed - (p*(fixed - opening)));
	}
	
	public void setP(double p) {
		this.p = p;
	}

	public double getAVPercentage(List<List<String>> recordAV) {
		int size = recordAV.size();
		int perc = (int) (size*(0.01));
		
		int numLists = 0;
		int numAV = 0;
		for(int i=size-perc; i<size; i++) {
			numAV += recordAV.get(i).size();
			numLists++;
		}
		
		if(numAV == 0 || numLists == 0) {
			return 1;
		}
		
		return (double)numAV/numLists;
	}
	
	public double getP() {
		return p;
	}
}
