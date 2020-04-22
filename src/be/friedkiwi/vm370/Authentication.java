package be.friedkiwi.vm370;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.FileNotFoundException;

public class Authentication {
	private static final String PASSWORD_FILE = "ems370.password";

	public static boolean validateUser(String username, String password) {
		username = username.toUpperCase();
		password = password.toUpperCase();
		
		
		
		// if the password file doesn't exist, allow all passwords.
		if (!authenticateUsers()) {
			return true;
		}
		
		try  {
			File passwordFile = new File(PASSWORD_FILE);
			FileReader passwordFileReader = new FileReader(passwordFile);
			BufferedReader pwReader = new BufferedReader(passwordFileReader);
			String line;
			
			try {
				while((line = pwReader.readLine()) != null) {
					line = line.trim();
					if ( line.length() > 0 &&  line.charAt(0) != '#' ) {
						String[] segments = line.split(":");
						if (segments[0].toUpperCase().equals(username) && segments[1].toUpperCase().equals(password)) {
							// password correct
							pwReader.close();
							return true;
						}
					}
				}
				pwReader.close();
			} catch (IOException ex) {
				// when an I/O error occurs while trying to read the file, deny access
				return false;
			}
		} catch (FileNotFoundException fnfex) {
			// when the password file doesn't exist, allow all access
			return true;
		}
		
		
		return false;
	}
	
	public static boolean authenticateUsers() {
		File passwordFile = new File(PASSWORD_FILE);
		return passwordFile.exists() && passwordFile.isFile();
	}
}
