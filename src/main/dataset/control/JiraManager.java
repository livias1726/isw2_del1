package main.dataset.control;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

import main.dataset.entity.Bug;
import main.utils.JSONManager;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Controller class.
 *
 * Uses REST-API to retrieve Jira information
 * */
public class JiraManager {

	private static String project;

	//Instantiation
	private static JiraManager instance = null;

	private JiraManager(String projName) {
		project = projName;
	}

	public static JiraManager getInstance(String projName) {
		if(instance == null) {
			instance = new JiraManager(projName);
		}

		project = projName;
		return instance;
	}

	/**
	 * Retrieves every version officially released.
	 *
	 * @return : map of every release and its release date
	 * */
	public Map<String, LocalDate> getProjectVersions() throws IOException {
		Map<String, LocalDate> releases = new LinkedHashMap<>();

		String url = "https://issues.apache.org/jira/rest/api/2/project/" + project;

		JSONArray versions = JSONManager.getInstance().readJsonFromUrl(url).getJSONArray("versions");
		JSONObject version;

		LocalDate startDate;
		String currVer;
		for(int i=0; i<versions.length(); i++){
			version = versions.getJSONObject(i);

			if(version.get("released").equals(true) && version.has("releaseDate")){
				startDate = LocalDate.parse(version.get("releaseDate").toString());
				currVer = version.get("name").toString();
				
				releases.put(currVer, startDate);
			}
		}

		//releases were not retrieved in the correct order of release date
		Map<String, LocalDate> orderedReleases = new LinkedHashMap<>();
		releases.entrySet().stream().sorted(Map.Entry.comparingByValue()).
				forEachOrdered(x -> orderedReleases.put(x.getKey(), x.getValue()));

		return orderedReleases;
	}

	/**
	 * Gets every jira ticket marked as a bug fix.
	 *
	 * @return : list of bug fixes
	 */
	public List<Bug> getFixes() throws IOException {
		int i = 0;
		int j;

		List<Bug> res;
		List<Bug> bugs = new ArrayList<>();
		while(true) {
			j = i + 100;
			String jiraUrl = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22"
					+ project
					+ "%22AND%22issuetype%22=%22Bug%22AND(%22status%22=%22Resolved%22OR%22status%22=%22Closed%22)"
					+ "AND%22resolution%22=%22Fixed%22ORDER%20BY%22createdDate%22ASC"
					+ "&startAt=" + i + "&maxResults=" + j;

			res = parseTickets(i, j, jiraUrl);
			if (res == null) {
				break;
			}

			i += res.size();
			bugs.addAll(res);
		}

		return bugs;
	}

	/**
	 * Parses the results of the REST-API invocation on tickets.
	 * From the retrieved information, the bugs are associated with:
	 * 		- The key
	 * 		- The opening date
	 * 		- The fix date
	 * 		- The affected versions
	 *
	 * @param i : counter for the results
	 * @param j : counter for the chunk in input
	 * @param url : url
	 *
	 * @return : list of bugs
	 * */
	private List<Bug> parseTickets(int i, int j, String url) throws IOException{
		
		List<Bug> bugs = null;

		JSONObject json = JSONManager.getInstance().readJsonFromUrl(url);
		JSONArray issues = json.getJSONArray("issues");
        int total = json.getInt("total");

		JSONObject issue;
		JSONObject fields;

		String key;
		LocalDate fixDate;
		List<String> affectedVersions;

        if(i<total) {
        	bugs = new ArrayList<>();
        	for (; i < total && i < j; i++) {
				issue = issues.getJSONObject(i%100);
				fields = issue.getJSONObject("fields");

				key = issue.get("key").toString();

				fixDate = LocalDate.parse(fields.getString("resolutiondate").substring(0,10));
				LocalDate openingDate = LocalDate.parse(fields.getString("created").substring(0,10));

				affectedVersions = parseJSONVersions(fields.getJSONArray("versions"));

				bugs.add(new Bug(key, openingDate, fixDate, affectedVersions));
            }  
        }
    	
		return bugs;
	}

	/**
	 * Parses the results of the REST-API invocation on versions.
	 *
	 * @param versions : array of JSONObject instances representing releases
	 *
	 * @return : list of releases
	 * */
	private List<String> parseJSONVersions(JSONArray versions) {
		List<String> list = new ArrayList<>();

		JSONObject version;
		for(int i=0; i<versions.length(); i++){
			version = versions.getJSONObject(i);
			list.add(version.get("name").toString());
		}

		return list;
	}

}