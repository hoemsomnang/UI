package application.instagram.service;

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
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v144.network.Network;
import org.openqa.selenium.devtools.v144.network.model.Response;

import application.instagram.constant.ExtensionConstant;
import application.instagram.utils.DateUtils;

public class InstagramDownloadFile {

	public  void downloadPhotos( String targetURL, String folder, DevTools devTools, ChromeDriver driver)
			throws Exception {

		try {
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
				downloadImage(folder, String.format("%s-(%s)", caption, index), url);
				index++;
			}
		} finally {
			if ( driver != null ) {
				driver.quit();
			}
		}

	}

	private  Set<String> getPhotosLink( String html ) throws Exception {
		
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
	
	
	private  String getPhotoCaption( String html ) throws Exception {
		
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
	public  String extractJsonFromHtml(String html, String contentName ) {
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
	public  String safeCaption(String caption) {
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
	public  void downloadImage(String profileName, String caption, String imageUrl) throws IOException {

		Path finalPath = Paths.get(profileName, caption + ExtensionConstant.JPG);
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
	
	
	/**
	 * @param downloadReel
	 * @param url
	 * @throws IOException
	 */
	public void downloadReel(String url, String profileName, DevTools devTools, ChromeDriver driver ) throws Exception {
		try {

			AtomicReference<String> htmlRef = new AtomicReference<>();

			Map<String, String> requestMap = new HashMap<>();
			// Capture HTML response
			devTools.addListener(Network.responseReceived(), response -> {
				Response res = response.getResponse();
				// Track HTML documents only
				if (res.getMimeType() != null && res.getMimeType().contains("html")) {
					requestMap.put(response.getRequestId().toString(), res.getUrl());
				}

			});
			// When loading finished
			devTools.addListener(Network.loadingFinished(), loading -> {
				String requestId = loading.getRequestId().toString();
				if (requestMap.containsKey(requestId)) {
					try {
						var body = devTools.send(Network.getResponseBody(loading.getRequestId()));
						String html = body.getBody();
						if (html != null && html.contains("representations")) {
							htmlRef.set(html);
						}
					} catch (Exception e) {
						e.printStackTrace();
						System.out.println("Cannot fetch body.");
						throw e;
					}
				}
			});
			driver.get(url);
			Thread.sleep(3000);
			// VERY IMPORTANT: remove listeners to avoid memory leaks
			devTools.clearListeners();
			String html = htmlRef.get();
			String json = extractJsonFromHtml(html);
			if (json == null) {
				throw new RuntimeException("Could not extract DASH JSON from HTML");
			}
			JSONObject root = new JSONObject(json);
			JSONArray reps = findRepresentations(root, "representations");
			if (reps == null || reps.isEmpty()) {
				throw new RuntimeException("No DASH 'representations' found");
			}
			processDashJson(reps, driver, profileName);
		} catch( Exception e ) {
			e.printStackTrace();
			System.out.println("cannot download error" + e.getMessage() );
			throw e;
		} 
	}
	
	/**
	 * @param driver
	 * @return
	 */
	public String getCaptionVDO(WebDriver driver) {

		JavascriptExecutor js = (JavascriptExecutor) driver;

        String caption = (String) js.executeScript("""
            function extractCaption() {

                // 1️⃣ Try JSON-LD (best source)
                const ld = document.querySelector('script[type="application/ld+json"]');
                if (ld) {
                    try {
                        const data = JSON.parse(ld.textContent);
                        const obj = Array.isArray(data) ? data[0] : data;
                        if (obj.caption) return obj.caption;
                        if (obj.description) return obj.description;
                    } catch(e){}
                }

                // 2️⃣ Fallback to og:description
                const og = document.querySelector("meta[property='og:description']");
                if (og && og.content) {
                    let text = og.content;

                    // Remove "username on Instagram: "
                    const index = text.indexOf(":");
                    if (index !== -1) {
                        text = text.substring(index + 1);
                    }

                    return text.trim().replace(/^"|"$|^“|”$/g, "");
                }

                return null;
            }
            return extractCaption();
        """);
        return caption == null ? DateUtils.getCurrentFormatDate(DateUtils.FORMAT_FULL_DATETIME) : caption.trim();

	}
	
	/**
	 * @param html
	 * @return
	 */
	public String extractJsonFromHtml(String html) {

		Document doc = Jsoup.parse(html);

		for (Element script : doc.select("script")) {
			String data = script.html();

			if (data.contains("all_video_dash_prefetch_representations")) {

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
	 * @param obj
	 * @param keyName
	 * @return
	 */
	public JSONArray findRepresentations(Object obj, String keyName ) {

		if (obj instanceof JSONObject jsonObj) {

			for (String key : jsonObj.keySet()) {

				if (key.equals( keyName )) {
					return jsonObj.getJSONArray(key);
				}

				Object child = jsonObj.get(key);

				JSONArray result = findRepresentations(child, keyName );
				if (result != null)
					return result;
			}
		}

		if (obj instanceof JSONArray arr) {
			for (int i = 0; i < arr.length(); i++) {
				JSONArray result = findRepresentations(arr.get(i), keyName );
				if (result != null)
					return result;
			}
		}

		return null;
	}
	

	// 🔥 DASH Processor
	/**
	 * @param representations
	 * @param driver
	 * @param profileName
	 * @throws Exception
	 */
	public void processDashJson(JSONArray representations, WebDriver driver, String profileName ) throws Exception {

		try {
			String bestVideoUrl = null;
			int bestVideoBandwidth = 0;

			String bestAudioUrl = null;
			int bestAudioBandwidth = 0;
			
			for (int i = 0; i < representations.length(); i++) {

				JSONObject rep = representations.getJSONObject(i);

				String mime = rep.getString("mime_type");
				int bandwidth = rep.getInt("bandwidth");

				String url = rep.getString("base_url").replace("\\/", "/").replace("\\u00253D", "%3D");

				if (mime.equals("video/mp4") && bandwidth > bestVideoBandwidth) {
					bestVideoBandwidth = bandwidth;
					bestVideoUrl = url;
				}

				if (mime.equals("audio/mp4") && bandwidth > bestAudioBandwidth) {
					bestAudioBandwidth = bandwidth;
					bestAudioUrl = url;
				}
			}

			if (bestVideoUrl != null && bestAudioUrl != null) {
				String caption = safeCaption(getCaptionVDO(driver));
				Path finalPath = Paths.get(profileName, "VDO", caption + ExtensionConstant.MP4);
				Map<String, String> ffHeaders = buildCdnHeaders(driver);
				mergeFromUrls(bestVideoUrl, bestAudioUrl, finalPath, ffHeaders);
				System.out.println("✅ Final file ready: " + finalPath);

			}
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}

	/**
	 * @param driver
	 * @return
	 */
	private Map<String, String> buildCdnHeaders(WebDriver driver) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
		headers.put("Accept", "*/*");
		headers.put("Referer", "https://www.instagram.com/");
		headers.put("Origin", "https://www.instagram.com");
		headers.put("Accept-Language", "en-US,en;q=0.9");

		// Optional: forward cookies from Selenium for private/locked content
		String cookieHeader = driver.manage().getCookies().stream().map(c -> c.getName() + "=" + c.getValue())
				.collect(Collectors.joining("; "));
		if (!cookieHeader.isBlank()) {
			headers.put("Cookie", cookieHeader);
		}
		return headers;
	}
	
	/**
	 * @param videoUrl
	 * @param audioUrl
	 * @param output
	 * @param headers
	 * @throws Exception
	 */
	private void mergeFromUrls(String videoUrl, String audioUrl, Path output, Map<String, String> headers)
			throws Exception {
		Files.createDirectories(output.getParent());

		// Build headers string (CRLF-separated) for ffmpeg
		String hdr = headers.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue())
				.collect(Collectors.joining("\r\n"));
		
		List<String> cmd = new ArrayList<>(List.of(
			    "ffmpeg", "-y", "-loglevel", "error",
			    "-threads", "4",             // Use multi-threading
			    "-headers", hdr, "-i", videoUrl,
			    "-headers", hdr, "-i", audioUrl,
			    "-c", "copy",                // Direct stream copy (no re-encoding)
			    "-map", "0:v:0",             // Specifically map first video stream
			    "-map", "1:a:0",             // Specifically map first audio stream
			    "-shortest", 
			    "-tcp_nodelay", "1",         // Speed up network packet delivery
			    output.toString()
			));

		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.redirectErrorStream(true); // collapse stderr/stdout
		Process p = pb.start();
		int exit = p.waitFor();

		if (exit != 0) {
			// Fallback: try re-encoding audio only (some IG audios are AAC already, some
			// may need aac)
			List<String> fallback = new ArrayList<>(List.of("ffmpeg", "-y", "-loglevel", "error", "-nostdin",
					"-headers", hdr, "-i", videoUrl, "-headers", hdr, "-i", audioUrl, "-c:v", "copy", "-c:a", "aac",
					"-b:a", "192k", "-shortest", "-movflags", "+faststart", output.toString()));
			ProcessBuilder pb2 = new ProcessBuilder(fallback);
			pb2.redirectErrorStream(true);
			int exit2 = pb2.start().waitFor();
			if (exit2 != 0) {
				throw new IOException("ffmpeg failed (even fallback). Exit codes: " + exit + ", " + exit2);
			}
		}
	}

	
}
