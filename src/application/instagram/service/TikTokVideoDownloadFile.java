package application.instagram.service;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v144.network.Network;
import org.openqa.selenium.devtools.v144.network.model.Response;

public class TikTokVideoDownloadFile {

	public void downloadVideo(String url, String folder, DevTools devTools, ChromeDriver driver) throws Exception {

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
					if (html != null && html.contains("webapp.video-detail")) {
						htmlRef.set(html);
					}
				} catch (Exception e) {
					throw e;
				}
			}
		});

		driver.get(url);
		Thread.sleep(5000);
		devTools.clearListeners();

		String html = htmlRef.get();
		if (html == null) {
			throw new RuntimeException("Could not capture TikTok HTML");
		}

		String json = extractJsonFromHtml(html);
		if (json == null) {
			throw new RuntimeException("Could not extract JSON");
		}

		JSONObject root = new JSONObject(json);
		JSONObject __DEFAULT_SCOPE__ = root.getJSONObject("__DEFAULT_SCOPE__");
		JSONObject webapp_video_detail = __DEFAULT_SCOPE__.getJSONObject("webapp.video-detail");
		// Description
		JSONObject shareMeta = webapp_video_detail.getJSONObject("shareMeta");
		String caption = shareMeta.getString("desc");
		// Video Download URL
		JSONObject itemInfo = webapp_video_detail.getJSONObject("itemInfo");
		JSONObject itemStruct = itemInfo.getJSONObject("itemStruct");
		JSONObject video = itemStruct.getJSONObject("video");
		// String downloadUrl = video.getString("downloadAddr");
		String downloadUrl = null;

		if (video.has("bitrateInfo")) {
			var arr = video.getJSONArray("bitrateInfo");
			int maxBitrate = 0;

			for (int i = 0; i < arr.length(); i++) {
				var obj = arr.getJSONObject(i);
				int bitrate = obj.getInt("Bitrate");

				if (bitrate > maxBitrate) {
					maxBitrate = bitrate;
					downloadUrl = obj.getJSONObject("PlayAddr").getJSONArray("UrlList").getString(0);
				}
			}
		}

		if (downloadUrl == null && video.has("PlayAddrStruct")) {
			downloadUrl = video.getJSONObject("PlayAddrStruct").getJSONArray("UrlList").getString(0);
		}
		String safeName = safeCaption(caption);

		Path output = Paths.get(folder, safeName + ".mp4");

		Map<String, String> headers = buildHeaders(driver);
		downloadFile(downloadUrl, output, headers);

	}

	// Extract JSON from HTML
	public static String extractJsonFromHtml(String html) {
		Document doc = Jsoup.parse(html);

		for (Element script : doc.select("script")) {
			String data = script.html();

			if (data.contains("itemStruct")) {
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

	// Download file
	private static void downloadFile(String fileUrl, Path output, Map<String, String> headers) throws Exception {

		Files.createDirectories(output.getParent());

		URL url = new URL(fileUrl);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();

		for (var h : headers.entrySet()) {
			conn.setRequestProperty(h.getKey(), h.getValue());
		}

		try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(output.toFile())) {

			byte[] buffer = new byte[8192];
			int len;

			while ((len = in.read(buffer)) != -1) {
				out.write(buffer, 0, len);
			}
		}
	}

	public Map<String, String> buildHeaders(ChromeDriver driver) {
		Map<String, String> headers = new LinkedHashMap<>();

		headers.put("User-Agent",
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122 Safari/537.36");
		headers.put("Accept", "*/*");
		headers.put("Connection", "keep-alive");
		headers.put("Referer", "https://www.tiktok.com/");
		headers.put("Origin", "https://www.tiktok.com");
		headers.put("Range", "bytes=0-");

		String cookieHeader = driver.manage().getCookies().stream().map(c -> c.getName() + "=" + c.getValue())
				.collect(Collectors.joining("; "));

		if (!cookieHeader.isBlank()) {
			headers.put("Cookie", cookieHeader);
		}

		return headers;
	}
}
