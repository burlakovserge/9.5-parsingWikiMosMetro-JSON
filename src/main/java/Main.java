import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    private static String dataFile = "src/main/resources/user.json";

    public static void main(String[] args) {
        String downloadURL = "https://ru.wikipedia.org/wiki/%D0%A1%D0%BF%D0%B8%D1%81%D0%BE%D0%BA_%D1%81%D1%82%D0%B0%D0%BD%D1%86%D0%B8%D0%B9_%D0%9C%D0%BE%D1%81%D0%BA%D0%BE%D0%B2%D1%81%D0%BA%D0%BE%D0%B3%D0%BE_%D0%BC%D0%B5%D1%82%D1%80%D0%BE%D0%BF%D0%BE%D0%BB%D0%B8%D1%82%D0%B5%D0%BD%D0%B0";
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        Document website;
        try {
            website = Jsoup.connect(downloadURL).maxBodySize(0).get();
            Elements webTable = website.select("div.mw-parser-output table").get(2).select("tr");
            List<String> lineNumbers = parseLineNumbers(webTable);

            /*создание дерева с номером линии : списком станций на этой линии*/
            TreeMap<String, List<String>> blockStationForJsonFile = new TreeMap<>();
            for (int i = 0; i < lineNumbers.size(); i++) {
                String currentLineNumber = lineNumbers.get(i);
                blockStationForJsonFile.put(currentLineNumber, parseStationNamesByLine(webTable, currentLineNumber));
            }

            /*создание списка объектов Line (номер и название линии)*/
            List<Line> blockLinesForJsonFile = new ArrayList<>();
            for (int i = 0; i < lineNumbers.size(); i++) {
                blockLinesForJsonFile.add(new Line(lineNumbers.get(i), parseLineNames(webTable).get(i)));
            }

            /*Запись в json файл, выше созданных блоков и соединений*/
            Metro metro = new Metro(blockStationForJsonFile, blockLinesForJsonFile, parseConnectionToJSON(webTable, website));
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File("src/main/resources/user.json"), metro);

            printFromJSON();

        } catch (JsonGenerationException e) {
            e.printStackTrace();
        } catch (JsonMappingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static List<String> parseLineNumbers(Elements webTable) {
        List<String> lineNumbers = new ArrayList<>();
        Elements lines = webTable.select("td:eq(0) span:eq(0)");
        for (Element line : lines) {
            lineNumbers.add(line.text());
        }
        return lineNumbers.stream().distinct().collect(Collectors.toList());
    }

    private static List<String> parseLineNames(Elements webTable) {
        List<String> lineNames = new ArrayList<>();
        Elements lines = webTable.select("td:eq(0) span:eq(1)");
        for (Element line : lines) {
            lineNames.add(line.attr("title"));
        }
        return lineNames.stream().distinct().collect(Collectors.toList());
    }

    private static List<String> parseStationNamesByLine(Elements webTable, String currentLineNumber) {
        List<String> stations = new ArrayList<>();
        for (int i = 1; i < webTable.size(); i++) {
            String numberLine = webTable.get(i).select("td:eq(0) span:eq(0)").text();
            String stationName = webTable.get(i).select("td:eq(1) > a, td:eq(1) span a").text();
            if (currentLineNumber.equals(numberLine)) {
                stations.add(stationName);
            }
        }
        return stations;
    }

    private static List<Connection> createConnection(int trNum, Elements webTable, Document website) {
        List<Connection> connection = new ArrayList<>();

        String numberLine = webTable.get(trNum).select("td:eq(0) span:eq(0)").text();
        String stationName = webTable.get(trNum).select("td:eq(1) > a, td:eq(1) span a").text();
        String[] numberConnectionLine = webTable.get(trNum).select("td:eq(3)").text().split("\\s");

        //для сравнения номеров станций. Если numberLine > 1го в массиве numberConnectionLine, значит это соединение ранее уже добавлено
        int numberLineInt = Integer.parseInt(numberLine.replaceAll("\\D", ""));
        int numberConnectionLineInt = Integer.parseInt(numberConnectionLine[0].replaceAll("\\D", ""));
        if (numberLineInt < numberConnectionLineInt) {
            connection.add(new Connection(numberLine, stationName));
            if (connectionStations(trNum, website).size() > 1) {
                for (int j = 0; j < connectionStations(trNum, website).size(); j++) {
                    connection.add(new Connection(numberConnectionLine[j], connectionStations(trNum, website).get(j)));
                }
            } else if (connectionStations(trNum, website).size() == 1) {
                connection.add(new Connection(numberConnectionLine[0], connectionStations(trNum, website).get(0)));
            }
        }
        return connection;
    }

    private static List<String> connectionStations(int trNum, Document website) {
        List<String> returnList = new ArrayList<>();

        Set<String> allStationNames = new LinkedHashSet<>(website.select("div.mw-parser-output table")
                .select("tr").select("td:eq(1) > a, td:eq(1) span a").eachText());
        List<String> namesOfConnectionStations = website.select("div.mw-parser-output table").get(2)
                .select("tr").get(trNum).select("td:eq(3) span").eachAttr("title");

        for (String station : allStationNames) {
            List<String> correctNamesOfConnectionStations = namesOfConnectionStations.stream().filter(s -> s.contains(station))
                    .map(s -> s = station).collect(Collectors.toList());
            if (correctNamesOfConnectionStations.size() != 0) {
                returnList.add(correctNamesOfConnectionStations.toString().replaceAll("[\\[\\]]", ""));
            }
        }
        return returnList;
    }

    private static List<List<Connection>> parseConnectionToJSON(Elements webTable, Document website) {
        List<List<Connection>> connectionsToJSON = new ArrayList<>();
        for (int i = 1; i < webTable.size(); i++) {
            if (!webTable.get(i).select("td:eq(3)").text().equals("")) {
                if (createConnection(i, webTable, website).size() != 0) {
                    connectionsToJSON.addAll(Collections.singleton(createConnection(i, webTable, website)));
                }
            }
        }
        return connectionsToJSON;
    }

    private static void printFromJSON() {
        try {
            JSONParser parser = new JSONParser();
            JSONObject jsonData = (JSONObject) parser.parse(getJsonFile());
            JSONObject stationsObject = (JSONObject) jsonData.get("stations");

            Set<Object> stationNumbers = new TreeSet<>(stationsObject.keySet());

          for (Object station : stationNumbers) {
              System.out.println("Линия № " + station + " содержит станций: "+ new ArrayList<>((Collection<String>) stationsObject.get(station)).size());
          }
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    private static String getJsonFile() {
        StringBuilder builder = new StringBuilder();
        try {
            List<String> lines = Files.readAllLines(Paths.get(dataFile));
            lines.forEach(line -> builder.append(line));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return builder.toString();
    }

//    private static void parseStations(JSONObject stationsObject) {
//        stationsObject.keySet().forEach(lineNumberObject ->
//        {
//            int lineNumber = Integer.parseInt((String) lineNumberObject);
//            //Line line = stationIndex.getLine(lineNumber);
//            JSONArray stationsArray = (JSONArray) stationsObject.get(lineNumberObject);
//
//            stationsArray.forEach(stationObject ->
//            {
//                Station station = new Station((String) stationObject, line);
//                stationIndex.addStation(station);
//                line.addStation(station);
//            });
//        });
//    }
}


