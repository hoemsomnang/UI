package application;

import application.instagram.controller.MainController;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class Main extends Application {
	private MainController controller;

	@Override
	public void start(Stage stage) {
		try {

			controller = new MainController();
			Scene scene = new Scene(controller.buildLayout(stage), 950, 700);
			scene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());
			// ✅ ADD ICON HERE
			stage.getIcons().add(new Image(getClass().getResource("/resources/logo_512.png").toExternalForm()));
			stage.setTitle("Lemon Tool Version 1.0.0");
			stage.setScene(scene);
			stage.show();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void stop() {
		System.out.println("Application closing...");
		if (controller != null) {
			controller.shutdown();
		}
		System.exit(0);
	}

	public static void main(String[] args) {
		launch(args);
	}

}