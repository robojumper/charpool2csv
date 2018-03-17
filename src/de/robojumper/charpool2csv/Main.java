package de.robojumper.charpool2csv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

	public static void main(String[] args) {
		Path p = Paths.get(args[0]);
		try {
			byte[] b = Files.readAllBytes(p);
			CharacterPool pool = new CharacterPool(b);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
