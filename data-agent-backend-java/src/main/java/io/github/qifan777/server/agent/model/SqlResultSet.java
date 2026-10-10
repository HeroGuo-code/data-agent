package io.github.qifan777.server.agent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SqlResultSet {
    private List<String> column = new ArrayList<>();
    private List<Map<String, String>> data = new ArrayList<>();
    private String errorMsg;

    public SqlResultSet() {}
    public SqlResultSet(List<String> column, List<Map<String,String>> data) {
        this.column = column;
        this.data = data;
    }

    public SqlResultSet clone() {
        List<Map<String,String>> dataCopy = new ArrayList<>();
        for (Map<String,String> row : data) dataCopy.add(new java.util.HashMap<>(row));
        return new SqlResultSet(new ArrayList<>(column), dataCopy);
    }

    public List<String> getColumn() { return column; }
    public void setColumn(List<String> column) { this.column = column; }
    public List<Map<String,String>> getData() { return data; }
    public void setData(List<Map<String,String>> data) { this.data = data; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }

    @Override public String toString() {
        return "SqlResultSet(column=" + column + ", data=" + data + ")";
    }
}
