package com.logmaster.util;

import com.google.gson.Gson;

import java.io.InputStream;
import java.io.InputStreamReader;

public class FileUtils {


    /**
     * Loads a definition resource from a JSON file
     *
     * @param classType the class into which the data contained in the JSON file will be read into
     * @param resource  the name of the resource (file name)
     * @param gson      a reference to the GSON object
     * @param <T>       the class type
     * @return the data read from the JSON definition file
     */
    public static <T> T loadDefinitionResource(Class<T> classType, String resource, Gson gson) {
        // Load the resource as a stream and wrap it in a reader
        InputStream resourceStream = classType.getResourceAsStream(resource);
        assert resourceStream != null;
        InputStreamReader definitionReader = new InputStreamReader(resourceStream);

        // Load the objects from the JSON file
        return gson.fromJson(definitionReader, classType);
    }
}
