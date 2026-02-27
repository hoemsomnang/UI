package application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v144.network.Network;

import application.instagram.model.PostData;
import application.instagram.service.InstagramDownloadFile;
import application.instagram.service.InstagramProfileScraper;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.DialogPane;
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
import javafx.stage.Stage;

public class Main extends Application {

	private TextField profileField;
	private TableView<PostData> table;
	private ObservableList<PostData> dataList;
	private ProgressBar progressBar;
	private TextArea cookiesArea; // Add to class fields
	private Button saveCookiesBtn;

	@Override
	public void start(Stage primaryStage) {
		try {
			// Top Section
			profileField = new TextField();
			profileField.setPromptText("Enter Instagram profile URL...");
			profileField.getStyleClass().add("modern-field");
			HBox.setHgrow(profileField, Priority.ALWAYS);

			Button scrapButton = new Button("Scrap Profile");
			scrapButton.getStyleClass().add("button-primary");

			Button downloadButton = new Button("Download Selected");
			downloadButton.getStyleClass().add("button-secondary");

			HBox topBox = new HBox(12, profileField, scrapButton, downloadButton);
			topBox.setPadding(new Insets(15));
			topBox.getStyleClass().add("top-bar");

			// Progress Bar (Modern Touch)
			progressBar = new ProgressBar(0);
			progressBar.setVisible(false);
			progressBar.setMaxWidth(Double.MAX_VALUE);

			// Table Setup
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

			table.getColumns().addAll(colNo, colLink, colType);
			table.setItems(dataList);
			// Inside your start() method:
			cookiesArea = new TextArea();
			cookiesArea.setPromptText("Paste Netscape format cookies here...");
			cookiesArea.setPrefHeight(150);

			saveCookiesBtn = new Button("💾 Save Cookies");
			saveCookiesBtn.getStyleClass().add("button-secondary");
			saveCookiesBtn.setMaxWidth(Double.MAX_VALUE);

			// Add action to the button
			saveCookiesBtn.setOnAction(e -> saveCookiesToDisk());

			VBox cookieBox = new VBox(8, new Label("Instagram Cookies (Netscape Format):"), cookiesArea, saveCookiesBtn);
			cookieBox.setPadding(new Insets(10));

			TitledPane cookiesPane = new TitledPane("Cookie Configuration", cookieBox);
			cookiesPane.setExpanded(false);

			// Auto-load cookies when the app starts
			loadCookiesFromDisk();

			VBox root = new VBox(0, topBox, cookiesPane, progressBar, table);
			root.getStyleClass().add("main-container");

			// Actions
			scrapButton.setOnAction(e -> startScraping());
			downloadButton.setOnAction(e -> handleDownload());

			Scene scene = new Scene(root, 900, 600);
			// Load the CSS file (see step 2 below)
			scene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());

			primaryStage.setTitle("Instagram Downloader Pro");
			primaryStage.setScene(scene);
			primaryStage.show();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void saveCookiesToDisk() {
		try {
			Files.writeString(Paths.get("cookies.txt"), cookiesArea.getText());
		} catch (IOException e) {
			System.err.println("Failed to save cookies: " + e.getMessage());
		}
	}

	private void loadCookiesFromDisk() {
		Path path = Paths.get("cookies.txt");
		if (Files.exists(path)) {
			try {
				cookiesArea.setText(Files.readString(path));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void handleDownload() {

		String profileName = profileField.getText();
		if (dataList.isEmpty()) {
			showAlert("No links to download!");
			return;
		}
		progressBar.setVisible(true);
		progressBar.setProgress(0);

		Task<Void> downloadTask = new Task<>() {
			@Override
			protected Void call() throws Exception {

				ChromeOptions options = new ChromeOptions();
				options.addArguments("--headless=new");
				options.addArguments("--disable-gpu");
				options.addArguments("--no-sandbox");
				options.addArguments("--disable-dev-shm-usage");
				options.addArguments("--window-size=1920,1080");
				options.addArguments("--disable-blink-features=AutomationControlled");
				options.addArguments("--blink-settings=imagesEnabled=false"); // Don't load images
				options.addArguments("--disable-extensions");
				options.addArguments("--page-load-strategy=eager"); // Don't wait for full scripts/ads

				ChromeDriver driver = new ChromeDriver(options);
				DevTools devTools = ((ChromeDriver) driver).getDevTools();
				devTools.createSession();
				devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
						Optional.empty()));

				// Create directory follow profile Name
				Path directory = Paths.get(profileName, "VDO");
				try {
					Files.createDirectories(directory);
					directory = Paths.get(profileName, "photos");
					Files.createDirectories(directory);
				} catch (IOException e) {
					e.printStackTrace();
				} // safe even if exists

				InstagramDownloadFile downloadService = new InstagramDownloadFile();
				int total = dataList.size();
				try {
					for (int i = 0; i < total; i++) {
						PostData post = dataList.get(i);

						// Update UI to show we are starting this specific download
						final double progress = (double) (i + 1) / total;
						javafx.application.Platform.runLater(() -> {
							post.setStatus("Downloading...");
							progressBar.setProgress(progress);
						});

						try {
							if ("Reel".equalsIgnoreCase(post.getType())) {
								// Note: For Reels, you might need to handle the DevTools/Driver lifecycle here
								downloadService.downloadReel(post.getLink(), profileName, devTools, driver);
							} else {
								downloadService.downloadPhotos(post.getLink(), profileName);
							}

							// Update UI to Success
							javafx.application.Platform.runLater(() -> post.setStatus("✅ Success"));
						} catch (Exception ex) {
							ex.printStackTrace();
							// Update UI to Failed
							javafx.application.Platform.runLater(() -> post.setStatus("❌ Failed"));
						}
					}
				} finally {
					driver.quit();
				}

				return null;
			}
		};

		downloadTask.setOnSucceeded(e -> {
			progressBar.setVisible(false);
			showAlert("All downloads processed!");
		});

		downloadTask.setOnFailed(e -> {
			progressBar.setVisible(false);
			showAlert("Batch download encountered a critical error.");
		});
		Thread thread = new Thread(downloadTask);
		thread.setDaemon(true);
		thread.start();
	}

	private void startScraping() {
		String profileUrl = profileField.getText();
		if (profileUrl.isEmpty()) {
			profileField.setStyle("-fx-border-color: #fd1d1d;");
			return;
		}
		profileField.setStyle("");

		// Clear previous data and show progress
		dataList.clear();
		progressBar.setVisible(true);
		progressBar.setProgress(-1);
		String rawCookies = cookiesArea.getText();
		Task<List<PostData>> scrapTask = new Task<>() {
			@Override
			protected List<PostData> call() throws Exception {
				// 1. Initialize Service
				InstagramProfileScraper scaperService = new InstagramProfileScraper();

				// 2. Execute scraping (This opens Selenium in headless mode)
				// Assuming the field contains the full URL, we extract the username
				String username = profileUrl.substring(profileUrl.lastIndexOf("/") + 1);
				Set<String> links = scaperService.scrapProfile(username, rawCookies );

				// 3. Map Set<String> to List<PostData>
				List<PostData> results = new ArrayList<>();
				int index = 1;
				for (String link : links) {
					String type = link.contains("/reel/") ? "Reel" : "Photo";
					results.add(new PostData(index++, link, type));
				}
				return results;
			}
		};

		// UI Updates on success
		scrapTask.setOnSucceeded(e -> {
			dataList.addAll(scrapTask.getValue());
			progressBar.setVisible(false);
			showAlert("Scraping Finished");
		});

		// UI Updates on failure
		scrapTask.setOnFailed(e -> {
			progressBar.setVisible(false);
			Throwable ex = scrapTask.getException();
			ex.printStackTrace();
			showAlert("Error: " + ex.getMessage());
		});

		// Run in a new thread so the UI doesn't freeze
		Thread thread = new Thread(scrapTask);
		thread.setDaemon(true); // Ensures thread closes if app is closed
		thread.start();
	}

	private void showAlert(String msg) {
		Alert alert = new Alert(Alert.AlertType.INFORMATION);
		alert.setTitle("Notification");
		alert.setHeaderText(null); // Removes the big ugly icon space
		alert.setContentText(msg);

		// Apply the same stylesheet as your main scene
		DialogPane dialogPane = alert.getDialogPane();
		dialogPane.getStylesheets().add(getClass().getResource("application.css").toExternalForm());
		dialogPane.getStyleClass().add("modern-alert");

		alert.showAndWait();
	}
}