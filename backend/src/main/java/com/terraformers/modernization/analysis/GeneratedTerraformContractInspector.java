package com.terraformers.modernization.analysis;

import com.terraformers.modernization.reference.AwsProviderSchemaCatalog;
import com.terraformers.modernization.reference.AwsProviderSchemaEvidence;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class GeneratedTerraformContractInspector {

    private static final Pattern DEPLOYABLE = Pattern.compile(
            "(?m)^\\s*(resource|module)\\s+\"([^\"]+)\"(?:\\s+\"[^\"]+\")?\\s*\\{");
    private final AwsProviderSchemaCatalog catalog;

    public GeneratedTerraformContractInspector(AwsProviderSchemaCatalog catalog) {
        this.catalog = catalog;
    }

    public void inspect(String terraform, AwsProviderSchemaEvidence evidence) {
        String source = terraform == null ? "" : terraform;
        Matcher matcher = DEPLOYABLE.matcher(source);
        while (matcher.find()) {
            if (matcher.group(1).equals("module")) {
                throw new GeneratedTerraformContractViolation("module blocks are outside the executable contract");
            }
            String type = matcher.group(2);
            if (!type.startsWith("aws_") || !catalog.contains(type)) {
                throw new GeneratedTerraformContractViolation("generated resource is outside the AWS provider contract");
            }
            if (!evidence.covers(type)) {
                throw new GeneratedTerraformContractViolation("generated resource is outside the request schema envelope");
            }
        }
    }
}
