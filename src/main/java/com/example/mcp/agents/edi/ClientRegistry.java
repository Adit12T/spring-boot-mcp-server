package com.example.mcp.agents.edi;

import com.example.mcp.agents.Json;
import com.example.mcp.agents.edi.Model.Client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads an environment's client list from clients.json. */
public class ClientRegistry {

    public Map<String, Client> load(Path environmentDir) throws IOException {
        Path file = environmentDir.resolve("clients.json");
        if (!(Json.parse(Files.readString(file)) instanceof List<?> rows)) {
            throw new IllegalArgumentException(file + " must contain a JSON array of clients");
        }
        Map<String, Client> clients = new LinkedHashMap<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> fields) || !(fields.get("id") instanceof String id)) {
                throw new IllegalArgumentException(file + " has a client entry without an id");
            }
            List<String> lines = new ArrayList<>();
            if (fields.get("linesOfBusiness") instanceof List<?> values) {
                values.forEach(value -> lines.add(String.valueOf(value)));
            }
            String name = fields.get("name") instanceof String text ? text : id;
            clients.put(id, new Client(id, name, Boolean.TRUE.equals(fields.get("enabled")), List.copyOf(lines)));
        }
        return clients;
    }
}
