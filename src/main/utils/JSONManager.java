package main.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;

/**
 * Manages the JSON API
 * */
public class JSONManager {

	//Instantiation
	private static JSONManager instance = null;
    private JSONManager() {/**/}
    public static JSONManager getInstance() {
        if(instance == null) {
        	instance = new JSONManager();
        }
        return instance;
    }
	
	public JSONObject readJsonFromUrl(String url) throws IOException{
		try (InputStream is = new URL(url).openStream(); BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
			String jsonText = readAll(rd);
			return new JSONObject(jsonText);
		}
	}
	
	public String readAll(Reader rd) throws IOException {
		StringBuilder sb = new StringBuilder();
		int cp;
		while ((cp = rd.read()) != -1) {
			sb.append((char) cp);
	    }
		
	    return sb.toString();
	}
}