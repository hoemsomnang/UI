package application.instagram.model;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class PostData {
	private final IntegerProperty id;
	private final StringProperty link;
	private final StringProperty type;
	private final StringProperty status; // New Property
	private final StringProperty mediaType; 
	private final StringProperty profileName; 

	public PostData(int id, String link, String type, String mediaType, String profileName) {
		this.id = new SimpleIntegerProperty(id);
		this.link = new SimpleStringProperty(link);
		this.type = new SimpleStringProperty(type);
		this.mediaType = new SimpleStringProperty(mediaType);
		this.profileName = new SimpleStringProperty(profileName);
		this.status = new SimpleStringProperty("Ready"); // Default value
	}

	// Getters for properties
	public IntegerProperty idProperty() {
		return id;
	}

	public StringProperty linkProperty() {
		return link;
	}

	public StringProperty typeProperty() {
		return type;
	}

	public StringProperty statusProperty() {
		return status;
	}
	
	public StringProperty mediaType() {
		return mediaType;
	}
	public StringProperty profileName() {
		return profileName;
	}

	// Standard getters/setters
	public String getLink() {
		return link.get();
	}

	public String getType() {
		return type.get();
	}
	
	public String getMediaTypee() {
		return mediaType.get();
	}
	public String getProfileName() {
		return profileName.get();
	}

	public void setStatus(String value) {
		this.status.set(value);
	}
	
	
}
