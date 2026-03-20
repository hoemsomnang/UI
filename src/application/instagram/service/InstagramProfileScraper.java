package application.instagram.service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import application.instagram.constant.DomainConstant;
import io.github.bonigarcia.wdm.WebDriverManager;

public class InstagramProfileScraper {

	public Set<String> scrapProfile(String profileName, String rawCookies) throws Exception {

		ChromeOptions options = new ChromeOptions();
		options.addArguments("--headless=new");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-blink-features=AutomationControlled");
		
		WebDriverManager.chromedriver().setup();
		WebDriver driver = new ChromeDriver(options);

		// Use LinkedHashSet to keep order and uniqueness
		Set<String> postLinks = new LinkedHashSet<>();

		try {
			
			driver.get( DomainConstant.INSTAGRAM );
			loadCookies(driver, rawCookies );
			driver.navigate().refresh(); // refresh after setting cookies to apply session

			String profileUrl = String.format("%s/%s", DomainConstant.INSTAGRAM, profileName );
			driver.get(profileUrl);

			WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
			// Wait for the grid to appear (grid items are <a> with /p/ or /reel/)
			wait.until(d -> d.findElements(By.cssSelector("a[href*='/p/'], a[href*='/reel/']")).size() > 0);

			final int MAX_SCROLLS = 200; // hard cap
			final int MAX_STALE_ROUNDS = 3; // how many consecutive "no change" rounds before giving up
			final int SLEEP_AFTER_SCROLL_MS = 1500;

			int staleRounds = 0;
			long lastHeight = getScrollHeight(driver);
			int lastCount = 0;

			for (int i = 0; i < MAX_SCROLLS; i++) {
				// Collect current anchors
				addVisiblePostLinks(driver, postLinks);

				// Scroll the last tile into view (helps trigger lazy loading more reliably than
				// page scroll)
				scrollLastTileIntoView(driver);

				// Small wait to let new content load
				Thread.sleep(SLEEP_AFTER_SCROLL_MS);

				// Re-collect
				addVisiblePostLinks(driver, postLinks);

				// Check conditions
				long currentHeight = getScrollHeight(driver);
				int currentCount = postLinks.size();

				boolean heightUnchanged = currentHeight == lastHeight;
				boolean countUnchanged = currentCount == lastCount;

				if (heightUnchanged && countUnchanged) {
					staleRounds++;
				} else {
					staleRounds = 0; // reset if we made progress
				}

				// Debug info (optional)
				System.out.printf("Scroll %d → height: %d (Δ %d), posts: %d (Δ %d), staleRounds: %d%n", i + 1,
						currentHeight, (currentHeight - lastHeight), currentCount, (currentCount - lastCount),
						staleRounds);

				lastHeight = currentHeight;
				lastCount = currentCount;

				// If we've seen no change for several rounds, assume end of feed
				if (staleRounds >= MAX_STALE_ROUNDS) {
					System.out.println("Reached end of profile feed (no more growth).");
					break;
				}
			}

			// Output
			postLinks.forEach(System.out::println);

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			driver.quit();
		}

		return postLinks;
	}

	private static void addVisiblePostLinks(WebDriver driver, Set<String> postLinks) {
		List<WebElement> anchors = driver.findElements(By.cssSelector("a[href*='/p/'], a[href*='/reel/']"));
		for (WebElement a : anchors) {
			try {
				String href = a.getAttribute("href");
				if (href != null && (href.contains("/p/") || href.contains("/reel/"))) {
					postLinks.add(href);
				}
			} catch (StaleElementReferenceException ignored) {
				// Skip stale elements; they can occur during dynamic updates
			}
		}
	}

	private static void scrollLastTileIntoView(WebDriver driver) {
		List<WebElement> anchors = driver.findElements(By.cssSelector("a[href*='/p/'], a[href*='/reel/']"));
		if (!anchors.isEmpty()) {
			WebElement last = anchors.get(anchors.size() - 1);
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", last);
		} else {
			// fallback: page scroll
			((JavascriptExecutor) driver).executeScript("window.scrollBy(0, 1500);");
		}
	}

	private static long getScrollHeight(WebDriver driver) {
		Object height = ((JavascriptExecutor) driver).executeScript("return document.body.scrollHeight;");
		if (height instanceof Long)
			return (Long) height;
		if (height instanceof Number)
			return ((Number) height).longValue();
		return -1L;
	}

	private static void loadCookies(WebDriver driver, String rawCookies) {
		if (rawCookies == null || rawCookies.isBlank())
			return;

		// Navigate to domain first
		driver.get("https://www.instagram.com");

		String[] lines = rawCookies.split("\n");
		for (String line : lines) {
			if (line.startsWith("#") || line.trim().isEmpty())
				continue;

			String[] tokens = line.split("\t");
			if (tokens.length >= 7) {
				try {
					String domain = tokens[0];
					String path = tokens[2];
					boolean isSecure = Boolean.parseBoolean(tokens[3]);
					// tokens[4] is expiry, but often 0 or long
					String name = tokens[5];
					String value = tokens[6];

					org.openqa.selenium.Cookie cookie = new org.openqa.selenium.Cookie.Builder(name, value)
							.domain(domain).path(path).isSecure(isSecure).build();

					driver.manage().addCookie(cookie);
				} catch (Exception e) {
					System.err.println("Error parsing cookie line: " + e.getMessage());
				}
			}
		}
		driver.navigate().refresh(); // Refresh to apply session
	}
	
}
