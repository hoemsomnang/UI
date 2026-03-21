package application.instagram.service;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONObject;

public class TikTokVideoDownloadFile {
	
	public  void downloadVideo(String videoUrl, String downloadPath ) {
		try {
			// Step 1: Call API
			String apiUrl = "https://tikwm.com/api/?url=" + videoUrl;

			HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("User-Agent", "Mozilla/5.0");

			BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));

			StringBuilder response = new StringBuilder();
			String line;

			while ((line = reader.readLine()) != null) {
				response.append(line);
			}

			reader.close();

			// Step 2: Parse JSON
			JSONObject json = new JSONObject(response.toString());
			JSONObject data = json.getJSONObject("data");

			String videoDownloadUrl = data.getString("play"); // no watermark
			String caption = data.getString("title");

			// Clean filename
			String safeCaption = caption.replaceAll("[^a-zA-Z0-9\\-]", "_");
			if (safeCaption.length() > 50) {
				safeCaption = safeCaption.substring(0, 50);
			}

			String fileName = downloadPath + "\\" + safeCaption + ".mp4";
			// Step 3: Download Video
			downloadFile(videoDownloadUrl, fileName);

			System.out.println("Download complete!");
			System.out.println("Video: " + fileName);
			System.out.println("Caption saved!");

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public  void downloadFile(String fileURL, String savePath) throws IOException {
		HttpURLConnection conn = (HttpURLConnection) new URL(fileURL).openConnection();
		conn.setRequestProperty("User-Agent", "Mozilla/5.0");

		InputStream inputStream = conn.getInputStream();
		FileOutputStream outputStream = new FileOutputStream(savePath);

		byte[] buffer = new byte[4096];
		int bytesRead;

		while ((bytesRead = inputStream.read(buffer)) != -1) {
			outputStream.write(buffer, 0, bytesRead);
		}

		outputStream.close();
		inputStream.close();
	}
}
