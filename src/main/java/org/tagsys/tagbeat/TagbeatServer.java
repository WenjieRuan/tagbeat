package org.tagsys.tagbeat;

import java.awt.Desktop;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.tagsys.tagbeat.commands.ChangeFrameSize;
import org.tagsys.tagbeat.commands.ChangeSampleNumber;
import org.tagsys.tagbeat.commands.ChangeSparsity;
import org.tagsys.tagbeat.commands.Filtering;
import org.tagsys.tagbeat.commands.Replay;
import org.tagsys.tagbeat.cr.CompressiveReading;

import com.google.gson.Gson;

import spark.Request;
import spark.Spark;
import spark.utils.StringUtils;

public class TagbeatServer {

	private static Gson gson = new Gson();
	
	private static WebSocketClient socketClient;
	
	private static Processor processor;

	public static void main(String[] args) throws URISyntaxException {

		System.out.println("start tagbeat...");

		Spark.port(9001);

		Spark.webSocket("/socket", WebSocketServer.class);

		Spark.externalStaticFileLocation("public");

		Spark.before((request, response) -> {
			response.header("Access-Control-Allow-Origin", "*");
			response.header("Access-Control-Request-Method", "*");
			response.header("Access-Control-Allow-Headers", "X-Requested-With");
		});

		Spark.init();

		Spark.get("/", (req, resp) -> {
			resp.redirect("/index.html");
			return "";
		});

		Spark.get("/discover", (req, resp) -> {

			return new JsonResult(0).toString();
		});

		Spark.post("/start", (req, resp) -> {

			try {

				@SuppressWarnings("unchecked")
				HashMap<String, String> body = (HashMap<String, String>) gson.fromJson(req.body(),
						new HashMap<String, String>().getClass());

				String tagseeIP = body.get("tagseeIP");
				String agentIP = body.get("agentIP");

				if (StringUtils.isEmpty(tagseeIP) || StringUtils.isEmpty(agentIP)) {

					return new JsonResult(1, "TagSee IP or agent IP cannot be empty.");

				}

				CloseableHttpClient httpclient = HttpClients.createDefault();

				String host = tagseeIP + "/service/agent/" + agentIP + "/start";

				HttpGet httpget = new HttpGet(host);

				CloseableHttpResponse response = httpclient.execute(httpget);

				int statusCode = response.getStatusLine().getStatusCode();

				if (statusCode == 200) {
					
					System.out.println("connect to Tagsee...");
					// please ensure tagsee has been started firstly.
					socketClient = new WebSocketClient(new URI("ws://localhost:9092/socket"));

					processor = new Processor(socketClient);
					
					System.out.println(response.getEntity().toString());
					
					return new JsonResult(0,response.getEntity().toString());
				} else {
					return new JsonResult(statusCode, response.getEntity().toString());
				}

			} catch (Exception e) {
				e.printStackTrace();
				return new JsonResult(500, e.getMessage());
			}

		});

		Spark.get("/stop", (req, resp) -> {

			try {

				@SuppressWarnings("unchecked")
				HashMap<String, String> body = (HashMap<String, String>) gson.fromJson(req.body(),
						new HashMap<String, String>().getClass());

				String tagseeIP = body.get("tagseeIP");
				String agentIP = body.get("agentIP");

				if (StringUtils.isEmpty(tagseeIP) || StringUtils.isEmpty(agentIP)) {

					return new JsonResult(1, "TagSee IP or agent IP cannot be empty.");

				}

				CloseableHttpClient httpclient = HttpClients.createDefault();

				String host = tagseeIP + "/service/agent/" + agentIP + "/stop";

				HttpGet httpget = new HttpGet(host);

				CloseableHttpResponse response = httpclient.execute(httpget);

				int statusCode = response.getStatusLine().getStatusCode();

				if (statusCode == 200) {
					
					if(socketClient!=null){
						processor.terminate();
						socketClient.disconnect();
						socketClient = null;
						processor = null;
					}
										
					return response.getEntity().toString();
				} else {
					return new JsonResult(statusCode, response.getEntity().toString());
				}

			} catch (Exception e) {
				e.printStackTrace();
				return new JsonResult(500, e.getMessage());
			}
		});

		Spark.get("/changeParam", (req, resp) -> {
			
			if(socketClient==null){
				return new JsonResult(1,"The system is not started.");
			}

			String NString = req.queryParams("N");
			String QString = req.queryParams("Q");
			String KString = req.queryParams("K");

			if (NString != null) {
				processor.addCommmand(new ChangeSampleNumber(Integer.parseInt(NString)));
			}

			if (QString != null) {
				processor.addCommmand(new ChangeFrameSize(Integer.parseInt(QString)));
			}

			if (KString != null) {
				processor.addCommmand(new ChangeSparsity(Integer.parseInt(KString)));
			}

			resp.type("application/json");

			return new JsonResult(0).toString();

		});

		Spark.get("/getFilters", (req, resp) -> {

			resp.type("application/json");
			JsonResult result = new JsonResult();

			result.put("filters", processor.getFilters());

			return result.toString();

		});

		Spark.post("/filtering", (req, resp) -> {

			String body = req.body();
			if (!StringUtils.isEmpty(body)) {
				@SuppressWarnings("unchecked")
				HashMap<String, Boolean> filters = (HashMap<String, Boolean>) gson.fromJson(body,
						new HashMap<String, Boolean>().getClass());

				processor.addCommmand(new Filtering(processor, filters));

			}

			return new JsonResult();

		});

		Spark.get("/history", (rep, resp) -> {

			JsonResult result = new JsonResult();

			List<String> filenames = Benchmark.getHistory();

			result.put("history", filenames);

			return result;

		});

		Spark.get("/replay", (rep, resp) -> {

			String filename = rep.queryParams("filename");

			processor.addCommmand(new Replay(processor, filename));

			return new JsonResult();

		});

		Spark.exception(Exception.class, (e, req, resp) -> {

			resp.status(200);
			resp.type("application/json");

			resp.body(new JsonResult(505, e.getMessage()).toString());

		});

		try {
			String url = "http://localhost:9001";
		

			 if (Desktop.isDesktopSupported()) {
			 // Windows
			 Desktop.getDesktop().browse(new URI(url));
			 } else {
			 // Ubuntu
			 Runtime runtime = Runtime.getRuntime();
			 runtime.exec("/usr/bin/firefox -new-window " + url);
			 }
		} catch (Exception e2) {
			System.out.println("It fails to open the default brower.");
		}

		System.out.println("You can access the dashboard at http://localhost:9001");

	}

}
