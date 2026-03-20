package application;

import java.io.File;
import java.io.FileWriter;
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

import application.instagram.constant.DomainConstant;
import application.instagram.model.PostData;
import application.instagram.service.InstagramDownloadFile;
import application.instagram.service.InstagramProfileScraper;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

public class Main extends Application {

    private TextField profileField;
    private TableView<PostData> table;
    private ObservableList<PostData> dataList;
    private ProgressBar progressBar;
    
    // Cookie UI
    private TextArea cookiesArea;
    private Button saveCookiesBtn;
    
    // Location UI
    private TextField locationField;
    private File selectedDirectory;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            // --- SECTION 1: Profile Input & Scraping ---
            profileField = new TextField();
            profileField.setPromptText("Enter Instagram profile URL...");
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

            // --- SECTION 3: Cookie Configuration ---
            cookiesArea = new TextArea();
            cookiesArea.setPromptText("Paste Netscape format cookies here...");
            cookiesArea.setPrefHeight(100);

            saveCookiesBtn = new Button("💾 Save Cookies");
            saveCookiesBtn.setMaxWidth(Double.MAX_VALUE);
            saveCookiesBtn.setOnAction(e -> saveCookiesToDisk());

            VBox cookieBox = new VBox(8, cookiesArea, saveCookiesBtn);
            cookieBox.setPadding(new Insets(10));
            TitledPane cookiesPane = new TitledPane("Cookie Configuration", cookieBox);
            cookiesPane.setExpanded(false);

            // --- SECTION 4: Progress & Table ---
            progressBar = new ProgressBar(0);
            progressBar.setVisible(false);
            progressBar.setMaxWidth(Double.MAX_VALUE);

            setupTable();
            loadCookiesFromDisk();

            // Assemble Layout
            VBox controls = new VBox(0, topBox, locationBox, cookiesPane);
            VBox root = new VBox(0, controls, progressBar, table);
            root.getStyleClass().add("main-container");

            // --- Button Actions ---
            browseBtn.setOnAction(e -> {
            	DirectoryChooser dc = new DirectoryChooser();
                dc.setTitle("Select Download Folder");
                File choice = dc.showDialog(primaryStage);
                if (choice != null) {
                    selectedDirectory = choice;
                    locationField.setText(choice.getAbsolutePath());
                    
                    // ADD THIS: Automatically load cookies from the new folder if they exist
                    loadCookiesFromDisk(); 
                }
            });

            scrapButton.setOnAction(e -> startScraping());
            downloadButton.setOnAction(e -> handleDownload());

            Scene scene = new Scene(root, 950, 700);
            scene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());

            primaryStage.setTitle("Instagram Downloader Pro");
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
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

        TableColumn<PostData, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(c -> c.getValue().statusProperty());
        colStatus.setMaxWidth(120);

        table.getColumns().addAll(colNo, colLink, colType, colStatus);
        table.setItems(dataList);
    }

    private void handleDownload() {
        if (dataList.isEmpty()) {
            showAlert("No links to download!");
            return;
        }

        String rawUrl = profileField.getText();
        if (rawUrl.isEmpty()) return;

        // Clean folder name from URL
        String profileName = rawUrl.substring(rawUrl.lastIndexOf("/") + 1).replaceAll("[^a-zA-Z0-9]", "_");
        
        // Use selected path or default to user home
        final Path basePath = (selectedDirectory != null) 
                                ? selectedDirectory.toPath() 
                                : Paths.get(System.getProperty("user.dir"));

        progressBar.setVisible(true);
        progressBar.setProgress(0);

        Task<Void> downloadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                ChromeOptions options = new ChromeOptions();
                options.addArguments("--headless=new", "--disable-gpu", "--no-sandbox");
                
                ChromeDriver driver = new ChromeDriver(options);
                DevTools devTools = driver.getDevTools();
                devTools.createSession();
                devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));

                // Create Target Folders
                Path profileFolder = basePath.resolve(profileName);
                Files.createDirectories(profileFolder.resolve("VDO"));
                Files.createDirectories(profileFolder.resolve("photos"));

                InstagramDownloadFile downloadService = new InstagramDownloadFile();
                int total = dataList.size();

                for (int i = 0; i < total; i++) {
                    PostData post = dataList.get(i);
                    final double progress = (double) (i + 1) / total;

                    javafx.application.Platform.runLater(() -> {
                        post.setStatus("Downloading...");
                        progressBar.setProgress(progress);
                    });

                    try {
                        if ("Reel".equalsIgnoreCase(post.getType())) {
                            downloadService.downloadReel(post.getLink(), profileFolder.toString(), devTools, driver);
                        } else {
                            downloadService.downloadPhotos(post.getLink(), profileFolder.toString());
                        }
                        javafx.application.Platform.runLater(() -> post.setStatus("✅ Success"));
                    } catch (Exception ex) {
                        javafx.application.Platform.runLater(() -> post.setStatus("❌ Failed"));
                    }
                }
                driver.quit();
                return null;
            }
        };

        downloadTask.setOnSucceeded(e -> {
            progressBar.setVisible(false);
            showAlert("Downloads complete in: " + basePath.resolve(profileName));
        });

        new Thread(downloadTask).start();
    }

    private void startScraping() {
        String profileUrl = profileField.getText();
        if (profileUrl.isEmpty()) return;

        dataList.clear();
        progressBar.setVisible(true);
        progressBar.setProgress(-1);

        Task<List<PostData>> scrapTask = new Task<>() {
            @Override
            protected List<PostData> call() throws Exception {
            	List<PostData> results = new ArrayList<>();
            	// Instagram Download
            	if ( profileUrl.contains(DomainConstant.INSTAGRAM ) ) {
            		InstagramProfileScraper scraper = new InstagramProfileScraper();
            		String username = profileUrl.split("instagram.com/")[1].split("\\?")[0];
                    Set<String> links = scraper.scrapProfile(username, cookiesArea.getText());
                    int index = 1;
                    for (String link : links) {
                        String type = link.contains("/reel/") ? "Reel" : "Photo";
                        results.add(new PostData(index++, link, type, "instagram"));
                    }
                // Tiktok Download
            	} else if ( profileUrl.contains(DomainConstant.TIKTOK ) ) {
            		
            	}
                
                return results;
            }
        };

        scrapTask.setOnSucceeded(e -> {
            dataList.addAll(scrapTask.getValue());
            progressBar.setVisible(false);
        });

        scrapTask.setOnFailed(e -> {
            progressBar.setVisible(false);
            showAlert("Scraping failed: " + scrapTask.getException().getMessage());
        });

        new Thread(scrapTask).start();
    }

    private void saveCookiesToDisk() {
        try {
        	
            String path = System.getProperty("user.home") + "\\" +"cookies\\" + "ig_cookies.txt";
            FileWriter writer = new FileWriter(path);
            writer.write(cookiesArea.getText());
            writer.close();
            showAlert("Cookies saved to: " + path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadCookiesFromDisk() {
    	try {
			
            Path path = Path.of(System.getProperty("user.home"), "cookies", "ig_cookies.txt");
            if (Files.exists(path)) {
                cookiesArea.setText(Files.readString(path));
                System.out.println("Cookies loaded from: " + path.toAbsolutePath());
            } else {
                // Optional: Clear area if switching to a folder with no cookies
                // cookiesArea.clear(); 
            }
        } catch (IOException e) {
            System.err.println("Could not load cookies: " + e.getMessage());
        }
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}