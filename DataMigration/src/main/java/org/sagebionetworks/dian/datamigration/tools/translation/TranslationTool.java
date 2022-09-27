package org.sagebionetworks.dian.datamigration.tools.translation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.internal.LinkedTreeMap;
import com.opencsv.CSVReader;
import com.opencsv.ICSVParser;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TranslationTool {
    public static void main(String[] args) throws IOException {
        exectue();
    }

    private static void exectue() throws IOException {

        // This file should be store at the root of the Git repo for the app,
        // in a folder names translations.
        File file = new File("translations/dian_tu_translations_2_0.csv");

        CSVReader reader = new CSVReader(new FileReader(file),
                ICSVParser.DEFAULT_SEPARATOR,
                ICSVParser.DEFAULT_QUOTE_CHARACTER,
                // Switching the default escape character will keep end lines (\n) in,
                // which is what we want.
                '~');

        String[] nextLine;

        List<LocaleResource> androidResourceList = new ArrayList<>();
        iOSLanguageMap iOSResourceList = new iOSLanguageMap();
        iOSResourceList.versions = new ArrayList<>();
        
        nextLine = reader.readNext();
        // A LinkedTreeMap will have the JSON stay alphabetically sorted per the CSV
        LinkedTreeMap<Integer, LinkedTreeMap<String, String>> localeMap = new LinkedTreeMap<>();
        for (int i = 1; i < nextLine.length; i++) {
            localeMap.put(i, new LinkedTreeMap<>());
        }

        while ((nextLine = reader.readNext()) != null) {
            // nextLine[] is an array of values from the line
            String translationKey = nextLine[0];
            for (int i = 1; i < nextLine.length; i++) {
                Map<String, String> keyMap = localeMap.get(i);
                String value = nextLine[i];

                // There is an issue with the translation file where they put 0 to designate blank
                if (value.equals("0")) {
                    value = "";
                }
                // Trim these fields because there is extra space
                if (LocaleResource.LANGUAGE_KEY.equals(translationKey) ||
                        LocaleResource.COUNTRY_KEY.equals(translationKey) ||
                        LocaleResource.APP_NAME.equals(translationKey)) {
                    value = value.trim();
                }
                // Skip any blank or "0" based translation keys
                if (!"0".equals(translationKey) &&
                    !"".equals(translationKey)) {
                    keyMap.put(translationKey, value);
                }
            }
        }
        
        for(Integer key : localeMap.keySet()) {
            LinkedTreeMap<String, String> iosMap = copyAndSanitize(localeMap.get(key));
            iOSLanguage language = new iOSLanguage();
            language.map = iosMap;
            iOSResourceList.versions.add(language);

            Map<String, String> translations = localeMap.get(key);
            // Android does not support the "app_name" translation key,
            // as this is done in build.gradle instead
            translations.remove(LocaleResource.APP_NAME);
            LocaleResource localeResource = new LocaleResource(translations);

            // A "0" is how the translation company marks a translation as
            // being blank, instead of just leaving it blank.
            if (localeResource.countryKey.equals("0") ||
                    localeResource.languageKey.equals("0")) {
                System.out.println("Ignoring invalid locale " +
                        localeResource.countryKey + "-" + localeResource.languageKey + ", ");
            } else {
                String title = localeResource.countryKey + "-" + localeResource.languageKey;
                if (!localeResource.isValid()) {
                    System.out.println("Invalid locale " + title);
                }
                androidResourceList.add(localeResource);
                new File("translations").mkdir();
                localeResource.write("translations/");
                System.out.println(title + ", ");
            }
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String iOSJson = gson.toJson(iOSResourceList);
        byte[] jsonBytes = iOSJson.getBytes(StandardCharsets.UTF_8);
        Path path = Paths.get("translations/iOSTranslations.json");
        Files.write(path, jsonBytes);
    }

    private static LinkedTreeMap<String, String> copyAndSanitize(
            LinkedTreeMap<String, String> mapToCopy) {
        LinkedTreeMap<String, String> newMap = new LinkedTreeMap<>();
        for (String mapkey : mapToCopy.keySet()) {
            String value = mapToCopy.get(mapkey)
                    // Bold
                    .replace("<b>", "*")
                    .replace("</b>", "*")
                    // Italics
                    .replace("<i>", "^")
                    .replace("</i>", "^")
                    // Underlined
                    .replace("<u>", "`")
                    .replace("</u>", "`")
                    // Strikethrough
                    .replace("<s>", "~")
                    .replace("</s>", "~")
                    // Endlines
                    .replace(" \n ", "\n")
                    .replace("\n ", "\n")
                    .replace("\\n", "\n")
                    .replace("\n", "\n")
                    // Breaks
                    .replace("<br>", "\n")
                    .replace("<br/>", "\n")
                    .replace("<br />", "\n")
                    // If we end up with 4 or 3 end lines, that's a mistake, make it 2
                    .replace("\n\n\n\n", "\n\n")
                    .replace("\n\n\n", "\n\n");
            newMap.put(mapkey, value);
        }
        return newMap;
    }

    public static class iOSLanguageMap {
        public List<iOSLanguage> versions;
    }

    public static class iOSLanguage {
        public LinkedTreeMap<String, String> map;
    }
}
