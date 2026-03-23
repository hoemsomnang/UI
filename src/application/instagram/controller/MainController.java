package application.instagram.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v144.network.Network;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;

import application.instagram.constant.DomainConstant;
import application.instagram.model.PostData;
import application.instagram.service.InstagramDownloadFile;
import application.instagram.service.InstagramProfileScraper;
import application.instagram.service.TikTokVideoDownloadFile;
import application.instagram.service.TiktokProfileScraper;
import application.instagram.utils.AlertUtil;
import io.github.bonigarcia.wdm.WebDriverManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

public class MainController {

	private TextField profileField;
	private TableView<PostData> table;
	private ObservableList<PostData> dataList;
	private ProgressBar progressBar;

	// Cookie UI
	//private TextArea cookiesArea;
	//private Button saveCookiesBtn;

	// Location UI
	private TextField locationField;
	private File selectedDirectory;
	private ChromeDriver driver;
	private DevTools devTools;

	public VBox buildLayout(Stage stage) {
		// --- SECTION 1: Profile Input & Scraping ---
		profileField = new TextField();
		profileField.setPromptText("Enter profile URL...");
		profileField.getStyleClass().add("modern-field");
		HBox.setHgrow(profileField, Priority.ALWAYS);

		Button scrapButton = new Button("Scrap Profile");
		scrapButton.getStyleClass().add("button-primary");

		HBox topBox = new HBox(12, profileField, scrapButton);
		topBox.setPadding(new Insets(15, 15, 5, 15));

		// --- SECTION 2: Download Location ---
		locationField = new TextField();
		locationField.setEditable(false);
		locationField.setPromptText("Default: Project Folder");
		locationField.getStyleClass().add("modern-field");
		HBox.setHgrow(locationField, Priority.ALWAYS);

		Button browseBtn = new Button("📁 Browse");
		browseBtn.getStyleClass().add("button-secondary");

		Button downloadButton = new Button("Download All");
		downloadButton.getStyleClass().add("button-success");

		HBox locationBox = new HBox(12, locationField, browseBtn, downloadButton);
		locationBox.setPadding(new Insets(5, 15, 15, 15));

		// --- SECTION 3: Cookie Configuration (UPGRADED) ---

		TextArea igCookiesArea = new TextArea();
		igCookiesArea.setPromptText("Instagram cookies (Netscape format)...");
		igCookiesArea.getStyleClass().add("modern-textarea");

		TextArea tiktokCookiesArea = new TextArea();
		tiktokCookiesArea.setPromptText("TikTok cookies...");
		tiktokCookiesArea.getStyleClass().add("modern-textarea");

		// Labels (modern look)
		Label igLabel = new Label("📸 Instagram Cookies");
		igLabel.getStyleClass().add("cookie-title");

		Label ttLabel = new Label("🎵 TikTok Cookies");
		ttLabel.getStyleClass().add("cookie-title");

		// Layout for each
		VBox igBox = new VBox(5, igLabel, igCookiesArea);
		igBox.getStyleClass().add("cookie-card");
		VBox ttBox = new VBox(5, ttLabel, tiktokCookiesArea);
		ttBox.getStyleClass().add("cookie-card");

		// Side-by-side layout
		HBox cookiesRow = new HBox(10, igBox, ttBox);
		HBox.setHgrow(igBox, Priority.ALWAYS);
		HBox.setHgrow(ttBox, Priority.ALWAYS);

		// Save button
		Button saveCookiesBtn = new Button("💾 Save Cookies");
		saveCookiesBtn.getStyleClass().add("button-primary");
		saveCookiesBtn.setMaxWidth(Double.MAX_VALUE);

		saveCookiesBtn.setOnAction(e -> {
		    saveCookiesToDisk(igCookiesArea.getText(), tiktokCookiesArea.getText());
		});

		// Final container
		VBox cookieBox = new VBox(10, cookiesRow, saveCookiesBtn);
		cookieBox.setPadding(new Insets(10));

		TitledPane cookiesPane = new TitledPane("🔐 Cookie Configuration", cookieBox);
		cookiesPane.setExpanded(false);

		// --- SECTION 4: Progress & Table ---
		progressBar = new ProgressBar(0);
		progressBar.setVisible(false);
		progressBar.setMaxWidth(Double.MAX_VALUE);

		setupTable();
		loadCookiesFromDisk(igCookiesArea, tiktokCookiesArea);
		loadLocationFromJson();

		// Assemble Layout
		VBox controls = new VBox(0, topBox, locationBox, cookiesPane);
		VBox root = new VBox(0, controls, progressBar, table);
		root.getStyleClass().add("main-container");

		// --- Button Actions ---
		browseBtn.setOnAction(e -> {
			DirectoryChooser dc = new DirectoryChooser();
			dc.setTitle("Select Download Folder");
			File choice = dc.showDialog(stage);
			if (choice != null) {
				selectedDirectory = choice;
				locationField.setText(choice.getAbsolutePath());
				// ADD THIS: Automatically load cookies from the new folder if they exist
				loadCookiesFromDisk(igCookiesArea, tiktokCookiesArea);
				// ✅ Save location immediately
				saveLocationToJson();
			}
		});

		scrapButton.setOnAction(e -> startScraping( stage,igCookiesArea.getText(), tiktokCookiesArea.getText() ));
		downloadButton.setOnAction(e -> handleDownload());

		return root;
	}

	private void setupTable() {
		table = new TableView<>();
		dataList = FXCollections.observableArrayList();
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<PostData, Number> colNo = new TableColumn<>("#");
		colNo.setCellValueFactory(c -> c.getValue().idProperty());
		colNo.setMaxWidth(50);

		TableColumn<PostData, String> colLink = new TableColumn<>("Post Link");
		colLink.setCellValueFactory(c -> c.getValue().linkProperty());

		TableColumn<PostData, String> colType = new TableColumn<>("Type");
		colType.setCellValueFactory(c -> c.getValue().typeProperty());
		colType.setMaxWidth(100);
		
		TableColumn<PostData, String> colMedia = new TableColumn<>("Media Type");
		colMedia.setCellValueFactory(c -> c.getValue().mediaType());
		colMedia.setMaxWidth(100);
		
		TableColumn<PostData, String> colStatus = new TableColumn<>("Status");
		colStatus.setCellValueFactory(c -> c.getValue().statusProperty());
		colStatus.setMaxWidth(120);
		
		

		table.getColumns().addAll(colNo, colLink, colType,colMedia, colStatus);
		table.setItems(dataList);
	}

	private void handleDownload() {
		if (dataList.isEmpty()) {
			AlertUtil.show("No links to download!");
			return;
		}

		if ( selectedDirectory == null ) {
			AlertUtil.show("Please choose folder to download!");
			return;
		}
		// Use selected path or default to user home
		final Path basePath = (selectedDirectory != null) ? selectedDirectory.toPath()
				: Paths.get(System.getProperty("user.dir"));

		progressBar.setVisible(true);
		progressBar.setProgress(0);

		Task<Void> downloadTask = new Task<>() {
			@Override
			protected Void call() throws Exception {
				ChromeOptions options = new ChromeOptions();

				Map<String, String> mobileEmulation = new HashMap<>();
				//mobileEmulation.put("deviceName", "iPhone 12 Pro");
				options = new ChromeOptions();
				options.addArguments("--headless");
				options.addArguments("--disable-gpu");
				options.addArguments("--no-sandbox");
				options.setExperimentalOption("mobileEmulation", mobileEmulation);
				options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
				driver = new ChromeDriver(options); 
				devTools = driver.getDevTools();
				devTools.createSession();
				devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
			
				int total = dataList.size();
				for (int i = 0; i < total; i++) {
					PostData post = dataList.get(i);
					final double progress = (double) (i + 1) / total;

					javafx.application.Platform.runLater(() -> {
						post.setStatus("Downloading...");
						progressBar.setProgress(progress);
					});
					String profileName = post.getProfileName();
					Path profileFolder = basePath.resolve(profileName);
					Files.createDirectories(profileFolder.resolve("VDO"));
					// Download With Instagram
					if ( DomainConstant.INSTAGRAM_DOW.equals(post.getMediaTypee()) ) {
						Files.createDirectories(profileFolder.resolve("photos"));
						InstagramDownloadFile downloadService = new InstagramDownloadFile();
						try {
							if ("Reel".equalsIgnoreCase(post.getType())) {
								downloadService.downloadReel(post.getLink(), profileFolder.toString(), devTools, driver);
							} else {
								downloadService.downloadPhotos(post.getLink(), profileFolder.toString(),devTools, driver );
							}
							javafx.application.Platform.runLater(() -> post.setStatus("✅ Success"));
						} catch (Exception ex) {
							javafx.application.Platform.runLater(() -> post.setStatus("❌ Failed"));
						}
					// Download With Tiktok
					} else if ( DomainConstant.TIKTOK_DOW.equals(post.getMediaTypee()) ) {
						TikTokVideoDownloadFile tiktokDownload = new TikTokVideoDownloadFile();
						try {
							tiktokDownload.downloadVideo(post.getLink(), profileFolder.resolve("VDO").toString(), devTools, driver);
							javafx.application.Platform.runLater(() -> post.setStatus("✅ Success"));
						} catch (Exception ex) {
							javafx.application.Platform.runLater(() -> post.setStatus("❌ Failed"));
						}
					}
				}
				driver.quit();
				return null;
			}
		};

		downloadTask.setOnSucceeded(e -> {
			progressBar.setVisible(false);
			AlertUtil.show("Downloads completed");
		});

		new Thread(downloadTask).start();
	}

	private void startScraping( Stage stage, String igCookiesArea, String tiktokCookiesArea ) {
		
		try {
			String profileUrl = profileField.getText();
			if (profileUrl.isEmpty()) {
				AlertUtil.showToast(stage,"Please input UR");
				return;
			}
			dataList.clear();
			progressBar.setVisible(true);
			progressBar.setProgress(-1);

			Task<List<PostData>> scrapTask = new Task<>() {
				@Override
				protected List<PostData> call() throws Exception {
					String username = "";
					List<PostData> results = new ArrayList<>();
					// Instagram Download
					if (profileUrl.contains(DomainConstant.INSTAGRAM)) {
						InstagramProfileScraper scraper = new InstagramProfileScraper();
						ChromeOptions options = new ChromeOptions();
						options.addArguments("--headless=new");
						options.addArguments("--disable-gpu");
						options.addArguments("--no-sandbox");
						options.addArguments("--disable-dev-shm-usage");
						options.addArguments("--window-size=1920,1080");
						options.addArguments("--disable-blink-features=AutomationControlled");
						WebDriverManager.chromedriver().setup();
						driver = new ChromeDriver(options);
						
						username = profileUrl.split("instagram.com/")[1].split("\\?")[0];
						Set<String> links = scraper.scrapProfile(username, igCookiesArea, driver );
						int index = 1;
						for (String link : links) {
							String type = link.contains("/reel/") ? "Reel" : "Photo";
							results.add(new PostData(index++, link, type, DomainConstant.INSTAGRAM_DOW, username));
						}
						// Tiktok Download
					} else if (profileUrl.contains(DomainConstant.TIKTOK)) {
						TiktokProfileScraper tiktokScraper = new TiktokProfileScraper();
						LoggingPreferences loggingPreferences = new LoggingPreferences();
						loggingPreferences.enable(LogType.PERFORMANCE, Level.ALL);
						ChromeOptions options = new ChromeOptions();
						options.setPageLoadStrategy(PageLoadStrategy.EAGER);
						options.addArguments("--headless=new");
						options.addArguments("--disable-gpu");
						options.addArguments("--no-sandbox");
						options.addArguments("--disable-dev-shm-usage");
						options.addArguments("--window-size=1920,1080");
						options.addArguments("--disable-blink-features=AutomationControlled");
						options.addArguments("--lang=en-US");
						options.addArguments("--remote-allow-origins=*");
						options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
								+ "AppleWebKit/537.36 (KHTML, like Gecko) " + "Chrome/133.0.0.0 Safari/537.36");
						options.setCapability("goog:loggingPrefs", loggingPreferences);
						WebDriverManager.chromedriver().setup();
						driver = new ChromeDriver(options);

						username = profileUrl.split("tiktok.com/")[1].split("\\?")[0];
						Set<String> links = tiktokScraper.scrapProfile(profileUrl, tiktokCookiesArea, driver );
						int index = 1;
						for (String link : links) {
							results.add(new PostData(index++, link, "Reel", DomainConstant.TIKTOK_DOW, username));
						}
					}
					return results;
				}
			};

			scrapTask.setOnSucceeded(e -> {
				dataList.addAll(scrapTask.getValue());
				progressBar.setVisible(false);
				AlertUtil.show("Scraping completed");
			});

			scrapTask.setOnFailed(e -> {
				progressBar.setVisible(false);
				AlertUtil.show("Scraping failed: " + scrapTask.getException().getMessage());
			});

			new Thread(scrapTask).start();
		} catch (Exception e) {
			throw e;
		}
	}

	
	private void saveCookiesToDisk(String igCookies, String ttCookies) {
		try {
			Path base = Path.of(System.getProperty("user.home"), "cookies");
			Files.createDirectories(base);

			Files.writeString(base.resolve("ig_cookies.txt"), igCookies);
			Files.writeString(base.resolve("tt_cookies.txt"), ttCookies);

			AlertUtil.show("Cookies saved successfully!");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void loadCookiesFromDisk(TextArea igArea, TextArea ttArea) {
		try {
			Path base = Path.of(System.getProperty("user.home"), "cookies");

			Path igPath = base.resolve("ig_cookies.txt");
			Path ttPath = base.resolve("tt_cookies.txt");

			if (Files.exists(igPath)) {
				igArea.setText(Files.readString(igPath));
			}

			if (Files.exists(ttPath)) {
				ttArea.setText(Files.readString(ttPath));
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void saveLocationToJson() {
		try {
			Path base = Path.of(System.getProperty("user.home"), "config");
			Files.createDirectories(base);

			Path file = base.resolve("config.json");

			String json = "{\n" + "  \"location\": \""
					+ (selectedDirectory != null ? selectedDirectory.getAbsolutePath().replace("\\", "\\\\") : "")
					+ "\"\n" + "}";

			Files.writeString(file, json);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void loadLocationFromJson() {
		try {
			Path base = Path.of(System.getProperty("user.home"), "config");
			Path file = base.resolve("config.json");

			if (!Files.exists(file))
				return;

			String json = Files.readString(file);

			String location = extractJsonValue(json, "location");

			if (!location.isEmpty()) {
				selectedDirectory = new File(location);
				locationField.setText(location);
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String extractJsonValue(String json, String key) {
		String pattern = "\"" + key + "\": \"";
		int start = json.indexOf(pattern);
		if (start == -1)
			return "";

		start += pattern.length();
		int end = json.indexOf("\"", start);

		return json.substring(start, end).replace("\\\\", "\\");
	}

	public void shutdown() {
		try {
			if (driver != null) {
				driver.quit();
				driver = null;
				System.out.println("ChromeDriver closed.");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
