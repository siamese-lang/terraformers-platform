package com.terraformers.modernization.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Standalone deterministic converter/aggregator; creates no Spring context. */
public final class CaseAMeasurementLauncher {
    private CaseAMeasurementLauncher() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> options = options(args);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Path output = Path.of(required(options, "output"));
        Files.createDirectories(output.toAbsolutePath().normalize().getParent());
        Object value;
        String mode = required(options, "mode");
        if (mode.equals("report")) {
            EvaluationRunResult run = mapper.readValue(Path.of(required(options, "result")).toFile(), EvaluationRunResult.class);
            EvaluationDataset dataset = new EvaluationDatasetLoader(mapper)
                    .load(Path.of(required(options, "dataset"))).dataset();
            value = new CaseAMeasurementReporter().create(run, dataset);
        } else if (mode.equals("aggregate")) {
            List<CaseAMeasurementReport> reports = Arrays.stream(required(options, "reports").split(","))
                    .map(Path::of).map(path -> read(mapper, path)).toList();
            value = new CaseAMultiRunAggregator().aggregate(reports);
        } else throw new IllegalArgumentException("mode must be report or aggregate");
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), value);
    }

    private static CaseAMeasurementReport read(ObjectMapper mapper, Path path) {
        try { return mapper.readValue(path.toFile(), new TypeReference<>() {}); }
        catch (Exception exception) { throw new IllegalStateException("failed to read report " + path, exception); }
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) throw new IllegalArgumentException("use --name=value");
            int split = arg.indexOf('=');
            if (values.put(arg.substring(2, split), arg.substring(split + 1)) != null) throw new IllegalArgumentException("duplicate option");
        }
        return values;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("--" + key + " is required");
        return value;
    }
}
