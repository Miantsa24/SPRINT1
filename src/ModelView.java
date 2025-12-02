package src;

import java.util.Map;
import java.util.LinkedHashMap;

public class ModelView {

    private String view;
    private Map<String, Object> data = new LinkedHashMap<>();

    public ModelView(String view) {
        this.view = view;
    }

    public String getView() {
        return view;
    }

    public void setView(String view) {
        this.view = view;
    }

    // récupérer les données
    public Map<String, Object> getData() {
        return data;
    }

    // ajouter une donnée
    public void addItem(String key, Object value) {
        data.put(key, value);
    }
}
