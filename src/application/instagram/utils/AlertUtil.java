package application.instagram.utils;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

public class AlertUtil {

	// Private constructor → prevent object creation
	private AlertUtil() {
	}

	public static void show(String message) {
		Stage dialog = new Stage();
		dialog.initStyle(javafx.stage.StageStyle.TRANSPARENT);

		Label title = new Label("Notification");
		title.getStyleClass().add("alert-title");

		Label msg = new Label(message);
		msg.setWrapText(true);

		Button okBtn = new Button("OK");
		okBtn.getStyleClass().add("button-primary");
		okBtn.setOnAction(e -> dialog.close());

		VBox layout = new VBox(15, title, msg, okBtn);
		layout.setPadding(new Insets(20));
		layout.setMaxWidth(300);
		layout.getStyleClass().add("modern-dialog");

		okBtn.setMaxWidth(Double.MAX_VALUE);

		Scene scene = new Scene(layout);
		scene.setFill(null);
		scene.getStylesheets().add(AlertUtil.class.getResource("/application/application.css").toExternalForm());

		dialog.setScene(scene);
		dialog.showAndWait();
	}

	public static void showToast(Stage owner, String message) {

		Popup popup = new Popup();

		Label label = new Label(message);
		label.getStyleClass().add("toast");

		StackPane root = new StackPane(label);
		root.getStyleClass().add("toast-container");

		popup.getContent().add(root);
		popup.setAutoFix(true);
		popup.setAutoHide(true);

		popup.show(owner);

		// Position (top-right)
		double x = owner.getX() + owner.getWidth() - 320;
		double y = owner.getY() + 20;
		popup.setX(x);
		popup.setY(y);

		// Animation (slide + fade)
		root.setOpacity(0);
		root.setTranslateY(-20);

		FadeTransition fadeIn = new FadeTransition(Duration.millis(300), root);
		fadeIn.setToValue(1);

		TranslateTransition slideIn = new TranslateTransition(Duration.millis(300), root);
		slideIn.setToY(0);

		PauseTransition stay = new PauseTransition(Duration.seconds(2));

		FadeTransition fadeOut = new FadeTransition(Duration.millis(300), root);
		fadeOut.setToValue(0);

		SequentialTransition seq = new SequentialTransition(new ParallelTransition(fadeIn, slideIn), stay, fadeOut);

		seq.setOnFinished(e -> popup.hide());
		seq.play();
	}
}