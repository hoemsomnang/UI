package application.instagram.service;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import application.instagram.constant.DomainConstant;

public class TiktokProfileScraper {


	private static final int WAIT_SECONDS = 15;
	private static final int MAX_SCROLLS = 120;
	private static final int MAX_IDLE_ROUNDS = 2;
	
	public Set<String> scrapProfile(String profileURL, String rawCookies, ChromeDriver driver ) throws Exception {
		
		Set<String> videoLinks = null;
		try {
			WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(WAIT_SECONDS));
			openHomeAndInjectCookies(driver, wait, rawCookies);
			videoLinks = scrapeProfileVideos(driver, wait, profileURL);
			for (String link : videoLinks) {
				log(link);
			}
		} catch (Exception e) {
			error("Fatal error in main", e);
		} finally {
			quitDriver(driver);
		}
		return videoLinks;
		
	}
	
	private  void openHomeAndInjectCookies(WebDriver driver, WebDriverWait wait, String cookieFile) {
		try {
			log("Opening TikTok home...");
			driver.get(DomainConstant.TIKTOK);

			wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
			sleep(1000);

			int injected = loadCookies(driver, cookieFile);
			log("Cookies injected: " + injected);

			driver.navigate().refresh();
			wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
			sleep(3000);

		} catch (TimeoutException e) {
			throw new RuntimeException("Timeout while opening TikTok home or refreshing after cookies", e);
		} catch (Exception e) {
			throw new RuntimeException("Failed in openHomeAndInjectCookies", e);
		}
	}
	
	private  Set<String> scrapeProfileVideos(WebDriver driver, WebDriverWait wait, String profileUrl) {
		Set<String> videoLinks = new LinkedHashSet<>();

		try {
			driver.get(profileUrl);

			wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
			sleep(3000);

			int idleRounds = 0;
			int previousCount = 0;

			for (int round = 1; round <= MAX_SCROLLS; round++) {
				int before = videoLinks.size();

				collectAllPossibleLinks(driver, videoLinks);

				int after = videoLinks.size();
				int added = after - before;

				log("Round " + round + " | total=" + after + " | added=" + added);

				if (after == previousCount) {
					idleRounds++;
				} else {
					idleRounds = 0;
					previousCount = after;
				}

				if (idleRounds >= MAX_IDLE_ROUNDS) {
					log("Stopping: no new links found for " + MAX_IDLE_ROUNDS + " rounds.");
					break;
				}

				smartScroll(driver);
				sleep(1500);
			}

		} catch (TimeoutException e) {
			error("Timeout while scraping profile", e);
		} catch (Exception e) {
			throw new RuntimeException("Failed while scraping profile videos", e);
		}

		return videoLinks;
	}

	private  void collectAllPossibleLinks(WebDriver driver, Set<String> videoLinks) {
		collectVideoLinksFromAnchors(driver, videoLinks);
		collectVideoLinksFromPageSource(driver, videoLinks);
		collectVideoLinksFromPerformanceLogs(driver, videoLinks);
	}

	private  void collectVideoLinksFromAnchors(WebDriver driver, Set<String> videoLinks) {
		for (int attempt = 1; attempt <= 3; attempt++) {
			try {
				List<WebElement> anchors = driver.findElements(By.cssSelector("a[href*='/video/']"));

				for (WebElement anchor : anchors) {
					try {
						String href = anchor.getAttribute("href");
						if (isValidTikTokVideoUrl(href)) {
							videoLinks.add(cleanUrl(href));
						}
					} catch (StaleElementReferenceException ignored) {
					} catch (Exception e) {
						log("Anchor read error: " + e.getMessage());
					}
				}
				return;

			} catch (StaleElementReferenceException e) {
				log("Stale elements while reading anchors, retry: " + attempt);
				sleep(1000);
			} catch (Exception e) {
				log("collectVideoLinksFromAnchors failed on attempt " + attempt + ": " + e.getMessage());
				sleep(1000);
			}
		}
	}

	private  void collectVideoLinksFromPageSource(WebDriver driver, Set<String> videoLinks) {
		try {
			String html = driver.getPageSource();
			if (html == null || html.isEmpty()) {
				return;
			}

			Pattern pattern = Pattern.compile("https://www\\.tiktok\\.com/@[^\"'\\\\]+/video/\\d+");
			Matcher matcher = pattern.matcher(html);

			while (matcher.find()) {
				String url = matcher.group();
				if (isValidTikTokVideoUrl(url)) {
					videoLinks.add(cleanUrl(url));
				}
			}

		} catch (Exception e) {
			log("collectVideoLinksFromPageSource error: " + e.getMessage());
		}
	}

	private  void collectVideoLinksFromPerformanceLogs(WebDriver driver, Set<String> videoLinks) {
		try {
			LogEntries logEntries = driver.manage().logs().get(LogType.PERFORMANCE);
			Pattern pattern = Pattern.compile("https://www\\.tiktok\\.com/@[^\"'\\\\]+/video/\\d+");

			for (LogEntry entry : logEntries) {
				try {
					String message = entry.getMessage();
					if (message == null || message.isEmpty()) {
						continue;
					}

					Matcher matcher = pattern.matcher(message);
					while (matcher.find()) {
						String url = matcher.group();
						if (isValidTikTokVideoUrl(url)) {
							videoLinks.add(cleanUrl(url));
						}
					}
				} catch (Exception e) {
					log("One performance log parse failed: " + e.getMessage());
				}
			}

		} catch (Exception e) {
			log("collectVideoLinksFromPerformanceLogs error: " + e.getMessage());
		}
	}

	private  void smartScroll(WebDriver driver) {
		try {
			JavascriptExecutor js = (JavascriptExecutor) driver;

			for (int i = 0; i < 6; i++) {
				js.executeScript("window.scrollBy(0, 900);");
				sleep(1200);
			}

			js.executeScript("window.scrollBy(0, -300);");
			sleep(1000);

		} catch (Exception e) {
			log("smartScroll error: " + e.getMessage());
		}
	}

	public int loadCookies(WebDriver driver, String rawCookies) {
		
		int successCount = 0;
		if (rawCookies == null || rawCookies.isBlank()) {
			return successCount; // Nothing to load
		}
		String[] lines = rawCookies.split("\\r?\\n");
		for (String line : lines) {
			try {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) {
					continue; // Skip empty lines and comments
				}

				String[] tokens = line.split("\t");
				if (tokens.length < 7) {
					log("Invalid cookie line skipped: " + line);
					continue;
				}

				String domain = tokens[0].trim();
				String path = tokens[2].trim();
				boolean secure = "TRUE".equalsIgnoreCase(tokens[3].trim());
				String name = tokens[5].trim();
				String value = tokens[6].trim();

				if (name.isEmpty()) {
					continue;
				}

				if (domain.startsWith(".")) {
					domain = domain.substring(1); // remove leading dot
				}

				Cookie cookie = new Cookie.Builder(name, value).domain(domain).path(path.isEmpty() ? "/" : path)
						.isSecure(secure).build();

				driver.manage().addCookie(cookie);
				successCount++;

			} catch (Exception e) {
				log("Failed to add one cookie: " + e.getMessage());
			}
		}
		return successCount;
	}

	private  boolean isValidTikTokVideoUrl(String url) {
		return url != null && url.matches("https://www\\.tiktok\\.com/@[^/]+/video/\\d+.*");
	}

	private  String cleanUrl(String url) {
		try {
			int idx = url.indexOf('?');
			return idx > -1 ? url.substring(0, idx) : url;
		} catch (Exception e) {
			return url;
		}
	}

	private  void quitDriver(WebDriver driver) {
		if (driver != null) {
			try {
				driver.quit();
			} catch (Exception e) {
				log("Error while quitting driver: " + e.getMessage());
			}
		}
	}

	private  void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Sleep interrupted", e);
		}
	}

	private  void log(String message) {
		System.out.println(message);
	}

	private  void error(String message, Exception e) {
		System.err.println(message + ": " + e.getMessage());
		e.printStackTrace();
	}
	
}
