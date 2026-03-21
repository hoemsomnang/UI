package application.instagram;

public class Test {

	public static void main(String[] args) {
		String profileUrl = "https://www.instagram.com/morgantlynds?igsh=MTl6ajNlNHM2c24xMg==";
		String username = profileUrl.split("instagram.com/")[1].split("\\?")[0];
		System.out.println(username);
	}

}
