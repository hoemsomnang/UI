package application.instagram;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v144.network.Network;
import org.openqa.selenium.devtools.v144.network.model.Response;

import application.instagram.constant.ExtensionConstant;

public class Test {

	public static void main(String[] args) throws Exception {

		ChromeDriver driver;
		DevTools devTools;
		ChromeOptions options = new ChromeOptions();

		Map<String, String> mobileEmulation = new HashMap<>();
		// mobileEmulation.put("deviceName", "iPhone 12 Pro");
		options = new ChromeOptions();
		options.addArguments("--headless");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.setExperimentalOption("mobileEmulation", mobileEmulation);
		options.addArguments(
				"--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
		driver = new ChromeDriver(options);
		devTools = driver.getDevTools();
		devTools.createSession();
		devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
				Optional.empty()));

		Path profileFolder = Paths.get("lemon");
		Files.createDirectories(profileFolder.resolve("photos"));
		downloadSphoto("lemon", "https://www.instagram.com/p/DS2e90GEz3J", "downloadTest", devTools, driver);
	}

	public static void downloadSphoto(String profileName, String targetURL, String folder, DevTools devTools, ChromeDriver driver)
			throws Exception {

		AtomicReference<String> htmlRef = new AtomicReference<>();
		Map<String, String> requestMap = new LinkedHashMap<>();

		// Capture HTML responses
		devTools.addListener(Network.responseReceived(), response -> {
			Response res = response.getResponse();
			if (res.getMimeType() != null && res.getMimeType().contains("html")) {
				requestMap.put(response.getRequestId().toString(), res.getUrl());
			}
		});

		// Capture HTML body
		devTools.addListener(Network.loadingFinished(), loading -> {
			String requestId = loading.getRequestId().toString();
			if (requestMap.containsKey(requestId)) {
				try {
					var body = devTools.send(Network.getResponseBody(loading.getRequestId()));
					String html = body.getBody();
					if (html != null && html.contains("xdt_api__v1__media__shortcode__web_info")) {
						htmlRef.set(html);
					}
				} catch (Exception e) {
					throw e;
				}
			}
		});

		driver.get(targetURL);
		Thread.sleep(3000);
		devTools.clearListeners();

		String html = htmlRef.get();
		if (html == null) {
			throw new RuntimeException("Could not capture TikTok HTML");
		}

		// Get Photos Link 
		Set<String> imagesURL = getPhotosLink(html);
		// Get Photo Caption 
		String caption = getPhotoCaption(html);
		int index = 1;
		for (String url : imagesURL) {
			// PROCESS DOWNLOAD IMAGE 
			caption = safeCaption(caption);
			downloadImage(profileName, String.format("%s-(%s)", caption, index), url);
			index++;
		}

	}

	private static Set<String> getPhotosLink( String html ) throws Exception {
		
		String json = extractJsonFromHtml(html, "xdt_api__v1__media__shortcode__web_info" );
		if (json == null) {
			throw new RuntimeException("Could not extract JSON");
		}

		JSONObject root = new JSONObject(json);
		// Step 1: require[0]
		JSONArray requireArray = root.getJSONArray("require");
		JSONArray firstRequire = requireArray.getJSONArray(0);
		// Step 2: index [3]
		JSONArray level3 = firstRequire.getJSONArray(3);
		// Step 3: first object
		JSONObject obj0 = level3.getJSONObject(0);
		// Step 4: __bbox.require[0]
		JSONArray bboxRequire = obj0.getJSONObject("__bbox").getJSONArray("require").getJSONArray(0);
		// Step 5: index [3][1]
		JSONArray innerArray = bboxRequire.getJSONArray(3);
		JSONObject target = innerArray.getJSONObject(1);
		// Step 6: navigate to items
		JSONArray items = target.getJSONObject("__bbox").getJSONObject("result").getJSONObject("data")
				.getJSONObject("xdt_api__v1__media__shortcode__web_info").getJSONArray("items");

		Set<String> imageUrls = new LinkedHashSet<>();
		int index = 1;
		for (int i = 0; i < items.length(); i++) {
			JSONObject item = items.getJSONObject(i);
			JSONArray carouselMedia = item.optJSONArray("carousel_media");
			if (carouselMedia != null && carouselMedia.length() > 0) {
				for (int j = 0; j < carouselMedia.length(); j++) {
					JSONObject media = carouselMedia.getJSONObject(j);
					String display_uri = media.getString("display_uri");
					if ( !display_uri.isBlank() ) {
						imageUrls.add(display_uri);
						index++;
					}
				}
			}
		}
		
		return imageUrls;
	}
	
	
	private static String getPhotoCaption( String html ) throws Exception {
		
		String text = "";
		String json = extractJsonFromHtml(html, "xdt_api__v1__profile_timeline" );
		if (json == null) {
			throw new RuntimeException("Could not extract JSON");
		}

		JSONObject root = new JSONObject(json);
		// Step 1: require[0]
		JSONArray requireArray = root.getJSONArray("require");
		JSONArray firstRequire = requireArray.getJSONArray(0);
		// Step 2: index [3]
		JSONArray level3 = firstRequire.getJSONArray(3);
		// Step 3: first object
		JSONObject obj0 = level3.getJSONObject(0);
		// Step 4: __bbox.require[0]
		JSONArray bboxRequire = obj0.getJSONObject("__bbox").getJSONArray("require").getJSONArray(0);
		// Step 5: index [3][1]
		JSONArray innerArray = bboxRequire.getJSONArray(3);
		JSONObject target = innerArray.getJSONObject(1);
		// Step 6: navigate to items
		JSONArray items = target.getJSONObject("__bbox").getJSONObject("result").getJSONObject("data")
				.getJSONObject("xdt_api__v1__profile_timeline").getJSONArray("items");
		for (int i = 0; i < items.length(); i++) {
			try {
				JSONObject item = items.getJSONObject(i);
				JSONObject caption = item.getJSONObject("caption");
				text =caption.getString("text");
			} catch ( Exception e) {
			}
			if ( !text.isBlank() ) {
				break;
			}
		}
		return text;
	}
	
	// Extract JSON from HTML
	public static String extractJsonFromHtml(String html, String contentName ) {
		Document doc = Jsoup.parse(html);

		for (Element script : doc.select("script")) {
			String data = script.html();

			if (data.contains( contentName )) {
				int start = data.indexOf("{");
				int end = data.lastIndexOf("}");
				if (start != -1 && end != -1) {
					return data.substring(start, end + 1);
				}
			}
		}
		return null;
	}

	/**
	 * @param caption
	 * @return
	 */
	public static String safeCaption(String caption) {
		if (caption == null || caption.isBlank())
			return "video_" + System.currentTimeMillis();
		// -------- Extract hashtags --------
		Matcher matcher = Pattern.compile("#\\w+").matcher(caption);
		List<String> tags = new ArrayList<>();
		while (matcher.find() && tags.size() < 3) {
			tags.add(matcher.group());
		}
		// remove all hashtags from caption text
		String textOnly = caption.replaceAll("#\\w+", "").trim();
		// rebuild caption: text + 3 hashtags
		String rebuilt = textOnly + " " + String.join(" ", tags);
		// remove only illegal filename chars (\ / : * ? " < > |)
		rebuilt = rebuilt.replaceAll("[\\\\/:*?\"<>|]", "");
		// collapse multiple spaces
		rebuilt = rebuilt.replaceAll("\\s+", " ").trim();

		// limit length to 100 chars
		if (rebuilt.length() > 100)
			rebuilt = rebuilt.substring(0, 100);

		if (rebuilt.isBlank())
			rebuilt = "video_" + System.currentTimeMillis();

		return rebuilt;
	}
	
	/**
	 * @param imageUrl
	 * @param savePath
	 * @throws IOException
	 */
	public static void downloadImage(String profileName, String caption, String imageUrl) throws IOException {

		Path finalPath = Paths.get(profileName, "photos", caption + ExtensionConstant.JPG);
		URL url = new URL(imageUrl);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
		connection.setConnectTimeout(10000);
		connection.setReadTimeout(10000);
		connection.connect();
		try (InputStream in = connection.getInputStream(); OutputStream out = Files.newOutputStream(finalPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			byte[] buffer = new byte[8192];
			int bytesRead;
			while ((bytesRead = in.read(buffer)) != -1) {
				out.write(buffer, 0, bytesRead);
			}
		}
		connection.disconnect();
	}

}