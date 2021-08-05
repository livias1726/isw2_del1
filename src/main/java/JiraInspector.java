package main.java;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/*
 * Get every closed issue of type BUG from Jira, 
 * with key and resolution date
 * */
public class JiraInspector {

	private String url;
	
	public JiraInspector(String jiraUrl) {
		this.url = jiraUrl;
	}

	public List<String> retrieveKeysFromJira(int i, int j) throws IOException{
		
		List<String> bugs = null;
		
		JSONObject json = JSONManager.getInstance().readJsonFromUrl(url);
		JSONArray issues = json.getJSONArray("issues");
        int total = json.getInt("total");
        
        if(i<total) {
        	bugs = new ArrayList<>();
        	for (; i < total && i < j; i++) {
        		bugs.add(issues.getJSONObject(i%50).get("key").toString());
            }  
        }
    	
		return bugs;
	}
}