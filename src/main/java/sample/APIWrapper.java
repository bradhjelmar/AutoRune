package sample;

import org.json.JSONArray;
import org.json.JSONObject;
import sample.data.Rune;
import sample.data.RuneTree;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/*
always show last date of data fetch with update button beside
if no serialized champ - rune list exists:
	automatically fetch and serialize
if serialized list is > 2 weeks old:
	prompt for update
eventlistener for champion lock in:
	get champion they are playing and their assigned role (will pull in all roles from champion.gg but will smart complete to their assigned role)
	if AutoRune page doesn't exist:
		if user has no available rune page slots:
			ask them if/which they would like to overwrite, exit if they choose none
		else
			create AutoRune page
	else
		deserialize champion - rune list
		show them all options for their champ
		apply user selection
 */
//TODO: get rune info from champion.gg (API? web scraper?)
//TODO: data read in from champion.gg is pretty much the final form, just need to mash it up with the rune IDs
//TODO: serialize/deserialize mechanism for this piece
//TODO: champion select lock in listener
//TODO: get selected champ and role
//TODO: delete rune page
//TODO: post rune page
//TODO: get list of all current rune pages
//TODO: UI... lmao

public class APIWrapper {

	String remotingAuthToken;
	String port;
	String pid;

	String currentLOLVersion;

	public APIWrapper() throws Exception {
		createFakeTrustManager();

		Runtime runtime = Runtime.getRuntime();
		Process proc = runtime.exec("wmic process where name='leagueclientux.exe' get commandline");
		InputStream inputstream = proc.getInputStream();
		InputStreamReader inputstreamreader = new InputStreamReader(inputstream);
		BufferedReader bufferedreader = new BufferedReader(inputstreamreader);
		String line;
		while ((line = bufferedreader.readLine()) != null) {
			if(line.contains("LeagueClientUx.exe")) {
				int beginningOfToken = line.indexOf("--remoting-auth-token=") + "--remoting-auth-token=".length();
				int endOfToken = line.indexOf("\"", beginningOfToken);
				remotingAuthToken = line.substring(beginningOfToken, endOfToken);

				int beginningOfPort = line.indexOf("--app-port=") + "--app-port=".length();
				int endOfPort = line.indexOf("\"", beginningOfPort);
				port = line.substring(beginningOfPort, endOfPort);

				int beginningOfPid = line.indexOf("--app-pid=") + "--app-pid=".length();
				int endOfPid = line.indexOf("\"", beginningOfPid);
				pid = line.substring(beginningOfPid, endOfPid);
			}
		}
		if(pid == null) {
			throw new Exception("Cannot find LeagueClientUx pid. Is league process running?");
		} else if(remotingAuthToken == null) {
			throw new Exception("Cannot find LeagueClientUx remoting-auth-token.");
		} else if(port == null) {
			throw new Exception("Cannot find LeagueClientUx port.");
		}
		currentLOLVersion = getCurrentLOLVersion();
	}

	public List<RuneTree> getAllRunes() throws IOException {
		String stringUrl = "http://ddragon.leagueoflegends.com/cdn/" + currentLOLVersion + "/data/en_US/runesReforged.json";
		StringBuffer result = makeHTTPCall(stringUrl);
		JSONArray jsonArray = new JSONArray(result.toString());

		List<RuneTree> runeTrees = new ArrayList<>();
		for(Object o : jsonArray){
			if(o instanceof JSONObject) {
				JSONObject runeTreeJSONObject = (JSONObject) o;
				JSONArray slots = (JSONArray) runeTreeJSONObject.get("slots");
				List<Rune> runeTreeRunes = new ArrayList<>();
				for(Object slot : slots) {
					if(slot instanceof JSONObject) {
						JSONObject slotJSONObject = (JSONObject) slot;
						JSONArray runesJSONArray = (JSONArray) slotJSONObject.get("runes");
						for(Object runeObject : runesJSONArray) {
							if(runeObject instanceof JSONObject) {
								JSONObject runeJSON = (JSONObject) runeObject;
								Rune rune = new Rune((String) runeJSON.get("name"), (Integer) runeJSON.get("id"));
								runeTreeRunes.add(rune);
							}
						}
					}
				}
				RuneTree runeTree = new RuneTree((String) runeTreeJSONObject.get("name"), (Integer) runeTreeJSONObject.get("id"), runeTreeRunes);
				runeTrees.add(runeTree);
			}
		}

		System.out.println(runeTrees);
		return runeTrees;
	}

	public String getCurrentLOLVersion() throws IOException {
		String stringUrl = "https://ddragon.leagueoflegends.com/api/versions.json";
		StringBuffer result = makeHTTPCall(stringUrl);
		List<String> items = Arrays.asList(result.toString().replaceAll("\"", "").replaceAll("^.|.$", "").split("\\s*,\\s*"));
		return items.get(0);
	}

	private StringBuffer makeHTTPCall(String stringUrl) throws IOException {
		URL url = new URL(stringUrl);
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("GET");

		String encoded = Base64.getEncoder().encodeToString(("riot" + ":" + remotingAuthToken).getBytes(StandardCharsets.UTF_8));
		con.setRequestProperty("Authorization", "Basic " + encoded);

		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer content = new StringBuffer();
		while ((inputLine = in.readLine()) != null) {
			content.append(inputLine);
		}
		in.close();

		return content;
	}

	private void createFakeTrustManager() {
		// Create a trust manager that does not validate certificate chains
		TrustManager[] trustAllCerts = new TrustManager[]{
			new X509TrustManager() {
				public java.security.cert.X509Certificate[] getAcceptedIssuers() {
					return null;
				}
				public void checkClientTrusted(
					java.security.cert.X509Certificate[] certs, String authType) {
				}
				public void checkServerTrusted(
					java.security.cert.X509Certificate[] certs, String authType) {
				}
			}
		};
		// Install the all-trusting trust manager
		try {
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		} catch (Exception e) {
		}
	}

}