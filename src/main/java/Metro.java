import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

public class Metro {
    private TreeMap stations;
    private List lines;
    private List connections;


    Metro(TreeMap stations, List lines, List connections) {
        this.stations = stations;
        this.lines = lines;
        this.connections = connections;
    }

    public TreeMap getStations() {
        return stations;
    }

    public void setStations(TreeMap stations) {
        this.stations = stations;
    }

    public List getConnections() {
        return connections;
    }

    public void setConnections(List connections) {
        this.connections = connections;
    }

    public List getLines() {
        return lines;
    }

    public void setLines(List lines) {
        this.lines = lines;
    }


}
